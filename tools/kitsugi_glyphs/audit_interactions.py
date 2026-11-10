#!/usr/bin/env python3
"""AST inventory of interactive Compose calls, including custom modifier surfaces.
Requires tree-sitter and tree-sitter-kotlin (development-only).
Each record is evidence, not a claim that a device screenshot has been verified.
"""
import argparse
import json
from pathlib import Path
import re
import tree_sitter
import tree_sitter_kotlin

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / 'app/src/main/java/com/kitsugi'
PARSER = tree_sitter.Parser(tree_sitter.Language(tree_sitter_kotlin.language()))
EVENTS = {'onClick', 'onLongClick', 'onCheckedChange', 'onValueChange', 'onSelect', 'onSelected', 'onTap', 'onDoubleTap', 'linkInteractionListener'}
MODIFIERS = re.compile(r'\b(?:clickable|combinedClickable|tvClickable|toggleable|selectable|copyOnDoubleTap|detectTapGestures|onKeyEvent|onPreviewKeyEvent|pointerInteropFilter|detectVerticalDragGestures|detectHorizontalDragGestures|detectDragGestures|detectTransformGestures)\b')
VISUALS = re.compile(r'\b(?:Icon|KitsugiMarkedText|KitsugiActionMark|KitsugiControlMark|SettingsIcon|KitsugiPlatformLogo|KitsugiSourceBadge|KitsugiSourceNamePill|AsyncImage|Image|Canvas|Checkbox|RadioButton|Switch|Slider|RangeSlider|CircularProgressIndicator|KitsugiPlasmaLoader)\s*\(')

def text(node, data): return data[node.start_byte:node.end_byte].decode()
def walk(node):
    yield node
    for child in node.named_children: yield from walk(child)

def parts(node, data):
    if node.type != 'call_expression': return None
    children = node.named_children
    if not children: return None
    first = children[0]
    if first.type == 'call_expression':
        nested = parts(first, data)
        if not nested: return None
        name, args, body = nested
    else:
        name = text(first, data).split('.')[-1]
        args = next((c for c in children if c.type == 'value_arguments'), None)
        body = None
    for c in children:
        if c.type == 'annotated_lambda': body = c
    return name, args, body

def inventory():
    rows=[]
    for path in sorted(SRC.rglob('*.kt')):
        data=path.read_bytes()
        tree=PARSER.parse(data)
        for node in walk(tree.root_node):
            result=parts(node,data)
            if not result:continue
            name,args,body=result
            if not re.fullmatch(r'[A-Z]\w*',name):continue
            # Inner invocation is represented again by the outer trailing-lambda call.
            if node.parent and node.parent.type=='call_expression' and node.parent.named_children[0]==node:continue
            if args is None:continue
            values=[c for c in args.named_children if c.type=='value_argument']
            named={text(c.named_children[0],data):c for c in values if len(c.named_children)>1 and c.named_children[0].type=='identifier'}
            events=EVENTS.intersection(named)
            modifier=named.get('modifier')
            mod=text(modifier,data) if modifier else ' '.join(text(v,data) for v in values if len(v.named_children) == 1 and re.match(r'(?:Modifier|modifier)\b', text(v,data)))
            if not events and not MODIFIERS.search(mod):continue
            # null handlers aren't independent click targets.
            if events and all(re.search(r'=\s*null\s*$',text(named[e],data)) for e in events) and not MODIFIERS.search(mod):continue
            code=text(node,data)
            func=node.parent
            while func and func.type!='function_declaration':func=func.parent
            fn=func.child_by_field_name('name') if func else None
            function=text(fn,data) if fn else '<top-level>'
            status='unresolved'
            evidence=''
            if path.name in ('KitsugiPlatformLogo.kt','KitsugiSourceBadge.kt'):
                status='source-logo-excluded';evidence='source component'
            elif 'KitsugiGlyphs.' in code:
                status='glyph';evidence='explicit Kitsugi vector in control'
            elif VISUALS.search(code):
                status='visual';evidence=VISUALS.search(code).group().strip()
            elif any(k in named for k in ('icon','imageVector','glyph','leadingIcon','trailingIcon')):
                status='icon-slot';evidence='caller-provided icon parameter'
            rows.append(dict(path=str(path.relative_to(ROOT)),line=data[:node.start_byte].count(b'\n')+1,end_line=data[:node.end_byte].count(b'\n')+1,start=node.start_byte,end=node.end_byte,name=name,function=function,status=status,evidence=evidence,code=code))
    for path in sorted(SRC.rglob('*.kt')):
        data = path.read_bytes()
        for node in walk(PARSER.parse(data).root_node):
            result = parts(node, data)
            if not result or not MODIFIERS.fullmatch(result[0]): continue
            if node.parent and node.parent.type == 'call_expression' and node.parent.named_children[0] == node: continue
            rel = str(path.relative_to(ROOT))
            if any(r['path'] == rel and r['start'] <= node.start_byte and r['end'] >= node.end_byte for r in rows): continue
            func = node.parent
            while func and func.type != 'function_declaration': func = func.parent
            ident = func.child_by_field_name('name') if func else None
            name = text(ident, data) if ident else '<top-level>'
            code = text(node, data)
            rows.append(dict(path=rel, line=data[:node.start_byte].count(b'\n')+1,
                end_line=data[:node.end_byte].count(b'\n')+1, start=node.start_byte, end=node.end_byte,
                name=result[0], function=name, status='unresolved-modifier', evidence='', code=code))
    definitions = {}
    functions = []
    for path in sorted(SRC.rglob('*.kt')):
        data = path.read_bytes()
        for node in walk(PARSER.parse(data).root_node):
            if node.type != 'function_declaration': continue
            ident = node.child_by_field_name('name')
            if ident is None: continue
            name = text(ident, data)
            code = text(node, data)
            functions.append((name, code, str(path.relative_to(ROOT))))
            # Evidence must live in this implementation; callsites remain listed separately.
            if 'KitsugiGlyphs.' in code or VISUALS.search(code):
                definitions.setdefault(name, []).append(str(path.relative_to(ROOT)))
    # Resolve custom composables transitively; keep generic content-slot wrappers
    # separate, because their caller supplies the visual rather than the wrapper.
    changed = True
    while changed:
        changed = False
        for name, code, path in functions:
            if name in definitions: continue
            called = set(re.findall(r'\b([A-Z]\w*)\s*\(', code)) - {name}
            if called.intersection(definitions):
                definitions[name] = [path]
                changed = True
    for row in rows:
        if row['status'] == 'unresolved' and row['name'] in definitions:
            row['status'] = 'delegated'
            row['evidence'] = 'implementation visual: ' + ', '.join(definitions[row['name']])
    for row in rows:
        if row['status'] != 'unresolved': continue
        code = row['code']
        if re.search(r'\b(?:content|label)\s*=\s*(?:content|label)\b', code) or re.search(r'\bcontent\s*\(\s*\)', code):
            row['status'] = 'content-slot'
            row['evidence'] = 'generic wrapper; content is audited at caller'
        elif re.search(r'(?:onClick|clickable)\s*(?:=\s*)?\{\s*(?://[^\n]*)?\s*\}', code):
            row['status'] = 'event-interceptor'
            row['evidence'] = 'no action; prevents click-through'
    from review_interactions import review
    owners = {}
    for name, code, path in functions:
        owners[(path, name)] = owners.get((path, name), '') + '\n' + code
    for row in rows:
        if not row['status'].startswith('unresolved'): continue
        result = review(row, owners.get((row['path'], row['function']), ''), (ROOT / row['path']).read_text())
        if result:
            row['status'], row['evidence'] = result
    return rows

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--output');ap.add_argument('--ledger');ap.add_argument('--check', action='store_true');args=ap.parse_args()
    rows=inventory()
    if args.output:Path(args.output).write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n')
    if args.ledger:
        Path(args.ledger).write_text(json.dumps([{k: r[k] for k in ('path','line','name','function','status','evidence')} for r in rows], ensure_ascii=False, indent=2) + '\n')
    from collections import Counter
    print('Interactive Compose calls:',len(rows),dict(Counter(r['status'] for r in rows)))
    for r in rows:
        if r['status'].startswith('unresolved'):print(f"{r['path']}:{r['line']} {r['function']} / {r['name']} :: {r['code'][:100].replace(chr(10),' ')}")

    if args.check and any(r['status'].startswith('unresolved') for r in rows):
        raise SystemExit(1)

if __name__=='__main__':main()
