import os.path
import token
from datetime import datetime
from typing import IO, Any, Dict, Iterator, List, Optional, Sequence, Set, Text, Tuple
import contextlib

from pegen import grammar
from pegen.grammar import (
    Alt,
    Cut,
    Forced,
    Gather,
    GrammarVisitor,
    Group,
    Lookahead,
    NamedItem,
    NameLeaf,
    NegativeLookahead,
    Opt,
    PositiveLookahead,
    Repeat0,
    Repeat1,
    Rhs,
    Rule,
    StringLeaf,
)
from pegen.parser_generator import (
    ParserGenerator,
    KeywordCollectorVisitor,
    RuleCollectorVisitor
)
from pegen.action_translator import ActionTranslator, JavaGeneratorContext


# Determine whether all those rules with names `invalid_XXX` for errors should be included:
WANT_INVALID_RULES = False

MODULE_PREFIX = """
// @generated by pegen from {filename}
package {parser_package};

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import {ast_package}.*;
//import {core_package}.*;  // only using primitives for now
"""

MODULE_SUFFIX = """
"""

CLASS_PREFIX = """\
private static class Memo<U> {
    public final U item;
    public int end_mark;

    Memo(U item, int end_mark) {
        this.item = item;
        this.end_mark = end_mark;
    }

    @Override
    public String toString() {
        if (item != null)
            return item.toString() + ":~" + end_mark;
        else
            return "<null>:~" + end_mark;
    }
}
"""


class InvalidNodeVisitor(GrammarVisitor):
    def visit_NameLeaf(self, node: NameLeaf) -> bool:
        name = node.value
        return name.startswith("invalid")

    def visit_StringLeaf(self, node: StringLeaf) -> bool:
        return False

    def visit_NamedItem(self, node: NamedItem) -> bool:
        return self.visit(node.item)

    def visit_Rhs(self, node: Rhs) -> bool:
        return any(self.visit(alt) for alt in node.alts)

    def visit_Alt(self, node: Alt) -> bool:
        return any(self.visit(item) for item in node.items)

    def lookahead_call_helper(self, node: Lookahead) -> bool:
        return self.visit(node.node)

    def visit_PositiveLookahead(self, node: PositiveLookahead) -> bool:
        return self.lookahead_call_helper(node)

    def visit_NegativeLookahead(self, node: NegativeLookahead) -> bool:
        return self.lookahead_call_helper(node)

    def visit_Opt(self, node: Opt) -> bool:
        return self.visit(node.node)

    def visit_Repeat(self, node: Repeat0) -> Tuple[str, str]:
        return self.visit(node.node)

    def visit_Gather(self, node: Gather) -> Tuple[str, str]:
        return self.visit(node.node)

    def visit_Group(self, node: Group) -> bool:
        return self.visit(node.rhs)

    def visit_Cut(self, node: Cut) -> bool:
        return False

    def visit_Forced(self, node: Forced) -> bool:
        return self.visit(node.node)


class JavaCallMakerVisitor(GrammarVisitor):
    def __init__(self, parser_generator: ParserGenerator):
        self.gen = parser_generator
        self.cache: Dict[Any, Any] = {}

    def visit_NameLeaf(self, node: NameLeaf) -> Tuple[Optional[str], str]:
        name = node.value
        if name == "SOFT_KEYWORD":
            return "soft_keyword", "this.softKeyword()"
        if name in ("default",):
            name = name + '_'
        if name in ("TYPE_COMMENT",):
            name = name.lower().title().replace('_', '')
            name = name[0].lower() + name[1:]
            return name, f"this.{name}()"
        if name in ("NAME", "NUMBER", "STRING", "OP"):
            name = name.lower()
            return name, f"this.{name}()"
        if name in ("NEWLINE", "DEDENT", "INDENT", "ENDMARKER", "ASYNC", "AWAIT"):
            # Avoid using names that can be Python keywords
            return "_" + name.lower(), f"this.expect(TokenType.{name})"
        return name, f"this.{name}()"

    def visit_StringLeaf(self, node: StringLeaf) -> Tuple[str|None, str]:
        if node.value and node.value[1:-1] in self.gen.keywords:
            func = "expectKeyword"
        else:
            func = "expectStr"
        if node.value and len(node.value) > 3 and node.value[0] == "'" == node.value[-1]:
            return None, f"this.{func}(\"{node.value[1:-1]}\")"   # "literal"
        return None, f"this.{func}({node.value})"

    def visit_Rhs(self, node: Rhs) -> Tuple[Optional[str], str]:
        if node in self.cache:
            return self.cache[node]
        if len(node.alts) == 1 and len(node.alts[0].items) == 1:
            self.cache[node] = self.visit(node.alts[0].items[0])
        else:
            name = self.gen.artifical_rule_from_rhs(node)
            self.cache[node] = name, f"this.{name}()"
        return self.cache[node]

    def visit_NamedItem(self, node: NamedItem) -> Tuple[Optional[str], str]:
        name, call = self.visit(node.item)
        if call.startswith("this.expectStr(") and name == 'literal':
            name = None
        if node.name:
            name = node.name
        return name, call

    def lookahead_call_helper(self, node: Lookahead) -> Tuple[str, str]:
        name, call = self.visit(node.node)
        head, tail = call.split("(", 1)
        assert tail[-1] == ")"
        tail = tail[:-1]
        return head, tail

    def visit_PositiveLookahead(self, node: PositiveLookahead) -> Tuple[None, str]:
        if (
            isinstance(node.node, Group) and
            all(len(n.items) == 1 and isinstance(n.items[0], NamedItem) and n.items[0].name is None
                for n in node.node.rhs.alts)
        ):
            items = [n.items[0].item for n in node.node.rhs.alts]
            if all([isinstance(i, StringLeaf) for i in items]):
                symbols = ', '.join(i.value.replace('\'', '\"') for i in items)
                return None, f"this.positive_lookahead({symbols})"

        head, tail = self.lookahead_call_helper(node)
        if head == "this.expect" and tail and tail[0] in "'\"":
            head = "this::expectStr"
        elif head.startswith("this."):
            head = "this::" + head[5:]
        if tail != "":
            return None, f"this.positive_lookahead({head}, {tail})"
        else:
            return None, f"this.positive_lookahead({head})"

    def visit_NegativeLookahead(self, node: NegativeLookahead) -> Tuple[None, str]:
        if (
            isinstance(node.node, Group) and
            all(len(n.items) == 1 and isinstance(n.items[0], NamedItem) and n.items[0].name is None
                for n in node.node.rhs.alts)
        ):
            items = [n.items[0].item for n in node.node.rhs.alts]
            if all([isinstance(i, StringLeaf) for i in items]):
                symbols = ', '.join(i.value.replace('\'', '\"') for i in items)
                return None, f"this.negative_lookahead({symbols})"

        #elif isinstance(node.node, StringLeaf):
        #    pass
        #elif isinstance(node.node, NameLeaf):
        #    pass

        head, tail = self.lookahead_call_helper(node)
        if head == "this.expect" and tail and tail[0] in "'\"":
            head = "this::expectStr"
        elif head.startswith("this."):
            head = "this::" + head[5:]
        if tail != "":
            return None, f"this.negative_lookahead({head}, {tail})"
        else:
            return None, f"this.negative_lookahead({head})"

    def visit_Opt(self, node: Opt) -> Tuple[str, str]:
        name, call = self.visit(node.node)
        # Note trailing comma (the call may already have one comma
        # at the end, for example when rules have both repeat0 and optional
        # markers, e.g: [rule*])
        if call.endswith(","):
            return "opt", call
        else:
            return "opt", f"{call}"  # <- removed comma at the end

    def visit_Repeat0(self, node: Repeat0) -> Tuple[str, str]:
        if node in self.cache:
            return self.cache[node]
        name = self.gen.artificial_rule_from_repeat(node.node, False)
        self.cache[node] = name, f"this.{name}()"  # Also a trailing comma! ## <- removed comma at the end
        return self.cache[node]

    def visit_Repeat1(self, node: Repeat1) -> Tuple[str, str]:
        if node in self.cache:
            return self.cache[node]
        name = self.gen.artificial_rule_from_repeat(node.node, True)
        self.cache[node] = name, f"this.{name}()"  # But no trailing comma here!
        return self.cache[node]

    def visit_Gather(self, node: Gather) -> Tuple[str, str]:
        if node in self.cache:
            return self.cache[node]
        name = self.gen.artifical_rule_from_gather(node)
        self.cache[node] = name, f"this.{name}()"  # No trailing comma here either!
        return self.cache[node]

    def visit_Group(self, node: Group) -> Tuple[Optional[str], str]:
        return self.visit(node.rhs)

    def visit_Cut(self, node: Cut) -> Tuple[str, str]:
        return "cut", "true"

    def visit_Forced(self, node: Forced) -> Tuple[str, str]:
        if isinstance(node.node, Group):
            _, val = self.visit(node.node.rhs)
            return "forced", f"this.expect_forced({val}, '''({node.node.rhs!s})''')"
        else:
            v = str(node.node.value)
            if v and v[0] in '\'\"':
                expt = f"this.expectStr({v})"
            else:
                expt = f"this.expect({node.node.value})"
            return (
                "forced",
                f"this.expect_forced({expt}, {node.node.value!r})",
            )


class AltContext:
    def __init__(self):
        self.tests: List[str] = []
        self.types: Dict[str, str] = {}

    def append(self, value):
        self.tests.append(value)

    def __setitem__(self, key, value):
        self.types[key] = value

    @property
    def items(self):
        return self.types.items()


class JavaParserGenerator(ParserGenerator, GrammarVisitor):
    def __init__(
        self,
        grammar: grammar.Grammar,
        generator_context: JavaGeneratorContext,
        file: Optional[IO[Text]],
        tokens: Optional[set] = None,
        location_formatting: Optional[str] = None,
        unreachable_formatting: Optional[str] = None,
        skip_actions: bool = False,
    ):
        if tokens is None:
            tokens = set(token.tok_name.values())
        tokens.add("SOFT_KEYWORD")
        super().__init__(grammar, tokens, file)

        self.generator_context = generator_context
        self.translator = ActionTranslator(generator_context)

        self.callmakervisitor: JavaCallMakerVisitor = JavaCallMakerVisitor(self)
        self.invalidvisitor: InvalidNodeVisitor = InvalidNodeVisitor()
        self.unreachable_formatting = unreachable_formatting or "null;  // pragma: no cover"
        self.location_formatting = (
            location_formatting
            or "start_lineno, start_col_offset, end_lineno, end_col_offset"
        )
        self.skip_actions = skip_actions
        # We need the *ALT-context* stack because of the typing required in Java.  For each
        # local variable we need to know the type and declare the variable before its use
        # in the IF text expression.  Furthermore, if it returns a Token, say, we may need
        # to wrap it later on so as to return a AST-node object.
        self._alt_context_stack: List[AltContext] = []
        self.current_rule_type = ''

    def collect_rules(self) -> None:
        keyword_collector = KeywordCollectorVisitor(self, self.keywords, self.soft_keywords)
        for rule in self.all_rules.values():
            keyword_collector.visit(rule)

        rule_collector = RuleCollectorVisitor(self.rules, self.callmakervisitor)
        done: Set[str] = set()
        while True:
            computed_rules = list(self.all_rules)
            todo = [i for i in computed_rules if i not in done]
            if not todo:
                break
            done = set(self.all_rules)
            for rulename in todo:
                if not WANT_INVALID_RULES and rulename.startswith('invalid_'):
                    continue
                rule_collector.visit(self.all_rules[rulename])

    @contextlib.contextmanager
    def alt_context(self) -> Iterator[None]:
        self._alt_context_stack.append(AltContext())
        yield
        self._alt_context_stack.pop()

    def alt_print(self, value):
        self._alt_context_stack[-1].append(value)

    def alt_print_tests(self, used_names: Optional[set] = None):
        with self.indent():
            tests = self._alt_context_stack[-1].tests
            assert len(tests) > 0
            if used_names:
                tsts = []
                for t in tests:
                    idx = t.find(' = ')
                    if idx > 0 and t[0] == '(':
                        if t[1] == '(':
                            nm = t[2:idx]
                            pf = '(('
                        else:
                            nm = t[1:idx]
                            pf = '('
                        if nm.isidentifier() and nm not in used_names:
                            tsts.append(pf + t[idx+3:])
                        else:
                            tsts.append(t)
                    else:
                        tsts.append(t)
                tests = tsts
            for t in tests[:-1]:
                self.print(t + " && ")
            self.print(tests[-1])

    def alt_print_types(self, used_names: Optional[set] = None):
        if used_names:
            for (n, tp) in self._alt_context_stack[-1].items:
                tp = self.translator.translate_type(tp)
                if n != "cut" and n in used_names:
                    self.print(f"{tp} {n};")
        else:
            for (n, tp) in self._alt_context_stack[-1].items:
                tp = self.translator.translate_type(tp)
                if n != "cut":
                    self.print(f"{tp} {n};")

    def _find_type_of_rule(self, node):
        if isinstance(node, NameLeaf):
            r = self.rules.get(node.value, None)
            if r is None:
                r = self.all_rules.get(node.value, None)
            if r is not None:
                return r.type
            elif node.value in ('star_target', 'star_targets', 'star_named_expression'):
                return 'expr_ty'
            elif node.value == 'star_named_expressions':
                return 'asdl_expr_seq*'
            elif node.value == 'NEWLINE':
                return 'Token'
            elif node.value == 'NAME':
                return 'Name'
            elif node.value == 'NUMBER':
                return 'expr_ty'
            elif node.value.isupper():
                return 'Token'
        elif (
            isinstance(node, Group) and
            len(node.rhs.alts) == 1 and
            len(node.rhs.alts[0].items) >= 2 and
            all(isinstance(itm, NamedItem) for itm in node.rhs.alts[0].items) and
            len(n_items := [itm.item for itm in node.rhs.alts[0].items
                            if isinstance(itm.item, NameLeaf) and not itm.item.value.isupper()]) == 1
        ):
            r = self.rules.get(n_items[0].value, None)
            if r is not None:
                return r.type
        elif isinstance(node, Group):
            return self._find_type_of_rule(node.rhs)
        elif isinstance(node, Rhs):
            types = [self._find_type_of_rule(itm) for itm in node.alts]
            types = [tp for tp in types if tp is not None]
            if len(set(types)) == 1:
                return types[0]
            elif types == ['asdl_arg_seq*', 'SlashWithDefault*']:
                return types[0]
            print("???", node, node.alts, types)
        elif isinstance(node, Alt):
            types = [self._find_type_of_rule(item) for item in node.items]
            types = [tp for tp in types if tp is not None]
            if len(types) == 1:
                return types[0]
            elif (
                all(t == 'Token' for t in types) and
                all(isinstance(itm, NamedItem) for itm in node.items) #and
                #len([itm for itm in node.items if not isinstance(itm.item, StringLeaf)]) == 1
            ):
                return 'Token'
            elif len(set(rtypes := [tp for tp in types if tp != 'Token'])) == 1:
                return rtypes[0]
            elif self._has_seq_action(node) and len(non_token_types := [t for t in types if t != 'Token']) == 2:
                for t in non_token_types:
                    if t.endswith('_seq*'):
                        return t
        elif isinstance(node, NamedItem):
            r = self._find_type_of_rule(node.item)
            return r
        elif isinstance(node, (Repeat0, Repeat1)):
            dtype = self._find_type_of_rule(node.node)
            dtype = dtype + '[]' if dtype is not None else dtype
            return dtype
        elif isinstance(node, Gather):
            dtype = self._find_type_of_rule(node.node)
            dtype = dtype + '[]' if dtype is not None else dtype
            return dtype
        elif isinstance(node, Opt):
            return self._find_type_of_rule(node.node)
        elif isinstance(node, StringLeaf):
            if node.value in ("'True'", "'False'", "'None'"):
                return 'expr_ty'
            return 'Token'
        elif isinstance(node, NegativeLookahead):
            return None
        # TODO: handle 'NEWLINE INDENT'
        # TODO: handle star_named_expression ',' star_named_expressions? in TUPLEs
        # TODO: handle expression for_if_clauses
        # Note: there are some cases where we deal with invalid syntax as part of the parser, which means there are no
        # valid types.
        #print("Unknown type", node.__class__.__name__, node)
        return None

    def artifical_rule_from_rhs(self, rhs: Rhs) -> str:
        self.counter += 1
        name = f"_tmp_{self.counter}"  # TODO: Pick a nicer name.
        tp = self._find_type_of_rule(rhs)
        self.all_rules[name] = Rule(name, tp, rhs)
        return name

    def artificial_rule_from_repeat(self, node: grammar.Plain, is_repeat1: bool) -> str:
        self.counter += 1
        if is_repeat1:
            prefix = "_loop1_"
        else:
            prefix = "_loop0_"
        name = f"{prefix}{self.counter}"
        tp = self._find_type_of_rule(node)
        self.all_rules[name] = Rule(name, tp, Rhs([Alt([NamedItem(None, node)])]))
        return name

    def artifical_rule_from_gather(self, node: Gather) -> str:
        self.counter += 1
        name = f"_gather_{self.counter}"
        self.counter += 1
        tp = self._find_type_of_rule(node.node)
        extra_function_name = f"_loop0_{self.counter}"
        extra_function_alt = Alt(
            [NamedItem(None, node.separator), NamedItem("elem", node.node)],
            action="elem",
        )
        self.all_rules[extra_function_name] = Rule(
            extra_function_name,
            tp,
            Rhs([extra_function_alt]),
        )
        alt = Alt(
            [NamedItem("elem", node.node), NamedItem("seq", NameLeaf(extra_function_name))],
        )
        self.all_rules[name] = Rule(
            name,
            tp,
            Rhs([alt]),
        )
        return name

    def get_type_of_variable(self, n):
        return self._alt_context_stack[-1].types.get(n, None)

    def get_local_var(self, index):
        var_name = self.local_variable_names[index]
        var_type = self.get_type_of_variable(var_name)
        if var_type == "Token" and False:
            return f"PyPegen.fromToken({var_name})"
        else:
            return str(var_name)

    def _is_token_type(self, name) -> bool:
        t = self._alt_context_stack[-1].types.get(name, None)
        return t == "Token"

    def set_local_type(self, name, _type):
        self._alt_context_stack[-1][name] = _type

    def parse_bool(self, value: Optional[str], name: str, default: bool) -> bool:
        if value is None:
            return default
        matches = {
            "false": False,
            "true": True,
        }
        cleaned = value.strip().lower()
        if cleaned not in matches:
            print(f"Unrecognized meta directive @{name} {value}")
        return matches.get(cleaned, default)

    def generate(self, filename: str) -> None:
        self.collect_rules()
        # TODO: respect given class name (or not?) in filename
        cls_name = self.grammar.metas.get(
            "class", self.generator_context.parser_name)
        self.print_header(filename, cls_name)
        for rule in self.all_rules.values():
            self.print()
            with self.indent():
                self.visit(rule)

        self.print()
        self.print_keywords("Keyword", tuple(self.keywords))
        self.print_keywords("SoftKeyword", tuple(self.soft_keywords))
        self.print_trailer(cls_name)

    def print_header(self, filename: str, cls_name: str) -> None:
        """
        Generates the header of the file by including the header defined above and adding the
        definition of the class ``GeneratedParser<T>``.
        """
        # header will be C: use our own
        basename = os.path.basename(filename)
        ctx = self.generator_context
        self.print(ctx.java_autogen_comment.rstrip('\n'))
        self.print(MODULE_PREFIX.strip('\n').format(
            filename=basename,
            core_package=ctx.core_package,
            ast_package=ctx.ast_package,
            parser_package=ctx.parser_package))

        # TODO: subheader likely to be C: translate?
        subheader = self.grammar.metas.get("subheader", "")
        if subheader:
            self.print(subheader)
        self.print("")
        self.print("// Keywords and soft keywords are listed at the end of the parser definition.")
        self.print(f"class {cls_name} extends Parser {{")
        with self.indent():
            self.print("")
            self.print(f"{cls_name}(Tokenizer tokenizer, String filename, boolean verbose) {{")
            with self.indent():
                self.print("super(tokenizer, filename, verbose);")
            self.print("}")
            self.print("")
            for line in CLASS_PREFIX.splitlines():
                self.print(line)

    def print_keywords(self, name: str, keywords: Tuple[str]) -> None:
        """
        Generates a list of the keywords used by the parser and prints that list to the
        generated Java class.  It overrides the Parser's ``isKeyword(s)`` method, which
        returns true if the string ``s`` is a keyword.
        """
        self.print()
        with self.indent():
            if not keywords:
                self.print("@Override")
                self.print(f"protected boolean is{name}(String name) {{")
                with self.indent():
                    self.print("return false;")
                self.print("}")
            else:
                self.print(f"private final Set<String> _{name.lower()}s = new HashSet<>(Arrays.asList(")
                with self.indent():
                    kws = sorted(keywords)
                    for kw in kws[:-1]:
                        self.print(f"\"{kw}\",")
                    self.print(f"\"{kws[-1]}\"")
                self.print("));")
                self.print()
                self.print("@Override")
                self.print(f"protected boolean is{name}(String name) {{")
                with self.indent():
                    self.print(f"return _{name.lower()}s.contains(name);")
                self.print("}")

    def print_trailer(self, cls_name: str):
        """
        Generates the trailer of the file.  This is usually the closing brace of the class definition.
        """
        self.print("}")
        # trailer = self.grammar.metas.get("trailer", MODULE_SUFFIX.format(class_name=cls_name))
        trailer = MODULE_SUFFIX.format(class_name=cls_name)
        if trailer is not None:
            self.print(trailer.rstrip("\n"))

    def alts_uses_locations(self, alts: Sequence[Alt]) -> bool:
        for alt in alts:
            if alt.action and "LOCATIONS" in alt.action:
                return True
            for n in alt.items:
                if isinstance(n.item, Group) and self.alts_uses_locations(n.item.rhs.alts):
                    return True
        return False

    def _is_seq(self, rule: str) -> bool:
        if rule in self.rules:
            r = self.rules[rule]
            if r.is_gather() or r.is_loop():
                return True
            t = r.type
            if t and "seq" in t:
                return True
            return False
        else:
            return "loop" in rule

    def _get_rule_type(self, rule: str) -> Optional[str]:
        r = self.rules.get(rule, None)
        if r is None:
            r = self.all_rules.get(rule, None)
        if r is not None:
            if r.is_loop():
                return r.type + '[]'
            return r.type
        else:
            return None

    def _has_seq_action(self, node) -> bool:
        if isinstance(node, Opt):
            node = node.node
        if isinstance(node, Rhs) and len(node.alts) == 1:
            node = node.alts[0]
        if isinstance(node, Alt):
            if node.action:
                action = node.action
                if action.startswith("_PyPegen_seq_insert_in_front"):
                    return True
                else:
                    for item in node.items:
                        if isinstance(item, NamedItem):
                            if item.name == action and isinstance(item.item, NameLeaf) and self._is_seq(item.item.value):
                                return True
        return False

    def visit_Rule(self, node: Rule) -> None:
        if not WANT_INVALID_RULES and node.name.startswith('invalid_'):
            return
        if node.name == 'func_type_comment':
            node.type = 'String'
        is_loop = node.is_loop()
        is_gather = node.is_gather()
        rhs = node.flatten()
        node_type = self.translator.translate_type(node.type)
        # print(node.name, ":", node.type, "->", node_type)
        if is_loop or is_gather: # or self._has_seq_action(rhs):
            # node_type = f"{node_type}[]"
            suffix = '[]'
        else:
            suffix = ''
        name = node.name
        if name in ("default",):        # Protect Java keywords
            name += '_'
        self.write_memoize_code(node, name, node_type + suffix)
        self.current_rule_type = node_type

        self.print(f"private {node_type}{suffix} _{name}() {{")
        with self.indent():
            self.print(f"// {node.name}: {rhs}")
            self.print("int mark = this.mark();")
            if self.alts_uses_locations(node.rhs.alts):
                self.print("tok = this.tokenizer.peek();")
                self.print("int start_lineno = tok.getStartLine();")
                self.print("int start_col_offset = tok.getStartOffset();")
            if is_loop:
                self.print(f"List<{node_type}> children = new ArrayList<>();")
            self.visit(rhs, is_loop=is_loop, is_gather=is_gather)
            if is_loop:
                # self.print(f"if (children.size() > 0) // {is_gather}")
                if node_type.endswith('[]'):
                    self.print(f"return children.toArray(new {node_type[:-2]}[children.size()][]);")
                else:
                    self.print(f"return children.toArray(new {node_type}[children.size()]);")
            else:
                self.print("return null;")
        self.print("}")

    def write_memoize_code(self, node: Rule, name: str, node_type: str):
        self.print(f"private final Map<Integer, Memo<{node_type}>> {name}_cache = new HashMap<>();")
        self.print("")
        self.print(f"protected {node_type} {name}() {{")
        with self.indent():
            if node.left_recursive:
                if node.leader:
                    self._write_memoize_left_rec(name, node_type)
                else:
                    self._write_logger(name, node_type)
            else:
                self._write_memoize(name, node_type)
        self.print("}")
        self.print("")

    def _write_memoize_left_rec(self, name: str, node_type: str) -> None:
        self.print("int p = this.mark();")
        self.print(f"Memo<{node_type}> info = {name}_cache.get(p);")
        self.print("if (info != null) {")
        with self.indent():
            self.print(f"log(\"{name}() [cached]-> \" + info.toString());")
            self.print("this.reset(info.end_mark);")
            self.print("return info.item;")
        self.print("}")
        self.print(f"logl(\"{name}() ...\");")
        self.print(f"{node_type} last_result = null;")
        self.print("int last_mark = p;")
        self.print("int depth = 0;")
        self.print(f"{name}_cache.put(p, new Memo<>(null, p));")
        self.print(f"log(\"recursive {name}() at \" + p + \" depth \" + depth);")
        self.print("while (true) {")
        with self.indent():
            self.print("this.reset(p);")
            self.print("this._level += 1;")
            self.print(f"{node_type} result = _{name}();")
            self.print("int end_mark = this.mark();")
            self.print("depth += 1;")
            self.print("this._level -= 1;")
            self.print(f"log(\"recursive {name}() at \" + p + \" depth \" + depth + \": \", result);")
            self.print("if (result == null || end_mark <= last_mark)")
            self.print("    break;")
            self.print("last_result = result;")
            self.print("last_mark = end_mark;")
            self.print(f"{name}_cache.put(p, new Memo<>(result, end_mark));")
        self.print("}")
        self.print("this.reset(last_mark);")
        self.print("if (last_result != null) {")
        with self.indent():
            self.print("last_mark = this.mark();")
        self.print("} else {")
        with self.indent():
            self.print("last_mark = p;")
            self.print("this.reset(last_mark);")
        self.print("}")
        self.print(f"log(\"{name}() [fresh]-> \", last_result);")
        self.print(f"{name}_cache.put(p, new Memo<>(last_result, last_mark));")
        self.print(f"return last_result;")

    def _write_memoize(self, name: str, node_type: str) -> None:
        self.print("int p = this.mark();")
        self.print(f"Memo<{node_type}> info = {name}_cache.get(p);")
        self.print("if (info != null) {")
        with self.indent():
            self.print(f"log(\"{name}() [cached]-> \" + info.toString());")
            self.print("this.reset(info.end_mark);")
            self.print("return info.item;")
        self.print("}")
        self.print(f"logl(\"{name}() ...\");")
        self.print("this._level += 1;")
        self.print(f"{node_type} result = _{name}();")
        self.print("this._level -= 1;")
        if (node_type.endswith('[]')):
            self.print(f"log(\"{name}() [fresh]-> \", Arrays.toString(result));")
        else:
            self.print(f"log(\"{name}() [fresh]-> \", result);")
        self.print("if (result != null) {")
        with self.indent():
            self.print(f"{name}_cache.put(p, new Memo<>(result, this.mark()));")
        self.print("}")
        self.print(f"return result;")

    def _write_logger(self, name: str, node_type: str) -> None:
        self.print(f"return _{name}();")

    def _is_upper(self, name):
        return all(n.isupper() for n in name if n != '_')

    def visit_NamedItem(self, node: NamedItem) -> None:
        #print(">>>", node.item.__class__.__name__, node.item)
        name, call = self.callmakervisitor.visit(node.item)
        if call.startswith("this.positive_lookahead") or call.startswith("this.negative_lookahead"):
            suffix = ""
        elif call in ("true", "false"):
            suffix = ""
        elif call.startswith("this._loop1_"): # or call.startswith("this._gather"):
            suffix = ".length > 0"
        else:
            suffix = " != null"

        if node.name:
            name = node.name
        if not name:
            txt = f"{call}{suffix}"
        elif name.startswith("invalid_"):
            txt = f"{call}{suffix}"
        else:
            if name != "cut":
                name = self.dedupe(name)
            txt = f"({name} = {call}){suffix}"

            # Determine type:
            if node.type is not None:
                dtype = self.translator.translate_type(node.type)
            elif isinstance(node.item, NameLeaf):
                if node.item.value == 'NAME':
                    dtype = 'Name'
                elif node.item.value == 'TYPE_COMMENT':
                    dtype = 'String'
                elif node.item.value == 'NUMBER':
                    dtype = 'ASTExpr'
                elif self._is_upper(node.item.value):
                    dtype = 'Token'
                else:
                    dtype = self._get_rule_type(node.item.value)
            elif isinstance(node.item, StringLeaf):
                dtype = 'Token'
            elif (
                    call.startswith("this.expect(") or call.startswith("this.expectStr(") or
                    call.startswith("this.expectKeyword(") or call.startswith("this.expect_forced(")
            ):
                dtype = 'Token'
            elif (
                    call.startswith("this.typeComment(")
            ):
                dtype = 'String'
            elif isinstance(node.item, (Repeat0, Repeat1, Gather, Group, Opt)):
                dtype = self._find_type_of_rule(node.item)
            else:
                dtype = None
            self.set_local_type(name, dtype)

        if isinstance(node.item, Opt):
            txt = f"({txt} || true)"
        self.alt_print(txt)

    def visit_Rhs(self, node: Rhs, is_loop: bool = False, is_gather: bool = False) -> None:
        if is_loop:
            assert len(node.alts) == 1
        for alt in node.alts:
            self.visit(alt, is_loop=is_loop, is_gather=is_gather)

    def visit_Alt(self, node: Alt, is_loop: bool, is_gather: bool) -> None:
        """
        The alternatives of a rule are tried one after another.  Each is a sequence of patterns,
        translated to ``if (a = p1() && b = p2() && ...) { return AST(a, b, ...); }``.
        """
        if not WANT_INVALID_RULES and self.invalidvisitor.visit(node):
            return
        has_cut = any(isinstance(item.item, Cut) for item in node.items)
        with self.local_variable_context():
            self.print("{")
            with self.indent():
                with self.alt_context():
                    if has_cut:
                        self.print("boolean cut = false;")
                    for item in node.items:
                        self.visit(item)
                    if len(self.local_variable_names) == 1 and not node.action and self.get_type_of_variable(0) is None:
                        self.set_local_type(self.get_local_var(0), self.current_rule_type)

                    action = node.action
                    if not action:
                        if is_gather:
                            assert len(self.local_variable_names) == 2
                            action = f"PyPegen.seqInsertInFront({self.get_local_var(0)}, {self.get_local_var(1)})"
                            used_names = { self.get_local_var(0), self.get_local_var(1) }
                        else:
                            if self.invalidvisitor.visit(node):
                                action = "UNREACHABLE"
                                used_names = set()
                            elif len(self.local_variable_names) == 1:
                                action = self.get_local_var(0)
                                used_names = { action }
                            elif len(self.local_variable_names) == 0:
                                if self.current_rule_type in ('Token', 'Object'):
                                    action = "getLastToken()"
                                elif self.current_rule_type == 'ASTExpr':
                                    action = "getLastTokenAsExpr()"
                                else:
                                    # This should not happen...
                                    action = f"!!!ERROR!!!; // {self.current_rule_type}"
                                used_names = set()
                            elif len(set(self.get_type_of_variable(n) for n in self.local_variable_names)) == 1:
                                action = "getLastToken()"
                                used_names = set()
                            else:
                                local_var_names = [self.get_local_var(i) for i in range(len(self.local_variable_names))]
                                action = f"PyPegen.auxMakeSeq({', '.join(local_var_names)})"
                                used_names = set(local_var_names)
                    else:
                        action, used_names = self.transform_action(action)

                    self.alt_print_types(used_names)

                    if '_start_lineno' in used_names:
                        self.print("int _start_lineno = this.getStartLineno();")
                    if '_start_col_offset' in used_names:
                        self.print("int _start_col_offset = this.getStartColOffset();")

                    if is_loop:
                        self.print("while (")
                    else:
                        self.print("if (")
                    self.alt_print_tests(used_names)
                    self.print(") {")
                    with self.indent():

                        if '_end_lineno' in used_names:
                            self.print("int _end_lineno = this.getEndLineno();")
                        if '_end_col_offset' in used_names:
                            self.print("int _end_col_offset = this.getEndColOffset();")

                        if self.skip_actions:
                            if is_loop:
                                self.print("children.add(PyPegen.dummyName());")
                                self.print("mark = this.mark();")
                            else:
                                self.print("return PyPegen.dummyName();")

                        elif is_loop:
                            #if self._is_seq(action):
                            #    self.print(f"children.addAll(Arrays.asList({action}));")
                            #else:
                            self.print(f"children.add({action});")
                            self.print(f"mark = this.mark();")
                        else:
                            if "UNREACHABLE" in action:
                                action = action.replace("UNREACHABLE", self.unreachable_formatting)
                            self.print(f"return {action};")

                self.print("}")
                self.print("this.reset(mark);")
                # Skip remaining alternatives if a cut was reached.
                if has_cut:
                    self.print("if (cut) { return null; }")
            self.print("}")

    def transform_action(self, action: Optional[str]) -> Optional[Tuple[str, set]]:
        """
        Since the actions were originally written for C (or for Python), we have to slightly transform
        them to be usable in a Java-environment.  This functions translates, e.g., ``_Py_BinOp (...)``
        to ``ast.BinOp (...)``, gets rid of some unnecessary variables and expands macros.
        """
        # The EXTRA macro is defined in `Parser/pegen.h`:
        # https://github.com/python/cpython/blob/c8a7b8fa1b5f9a6d3052db792018dc27c5a3a794/Parser/pegen.h#L155
        # Note that we do not expand the full macro here.
        if action is None:
            return None, set()
        else:
            return self.translator.translate_action(action)
