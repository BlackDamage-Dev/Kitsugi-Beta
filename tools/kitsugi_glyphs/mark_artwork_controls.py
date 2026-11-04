#!/usr/bin/env python3
"""Give artwork-only targets a semantic action mark without replacing their images."""
import json
from pathlib import Path
from collections import defaultdict
from audit_interactions import ROOT, PARSER, walk, parts, text
from migrate_material_icons import add_import

FUNCTIONS = {
'CollectionFolderCardMedia':'Folder','KitsugiActivityDetailBottomSheet':'Profile',
'KitsugiAllActivitiesBottomSheet':'Profile','KitsugiAllReviewsBottomSheet':'Profile',
'KitsugiAllTopicsBottomSheet':'Profile','KitsugiForumCommentCard':'Profile',
'KitsugiForumChildCommentRow':'Profile','KitsugiReviewDetailBottomSheet':'Profile',
'KitsugiTopicDetailBottomSheet':'Profile','MediaAppearanceRow':'Film','VoiceActorRow':'Profile',
'CharactersTabContent':'Profile','CharacterVoiceActorCard':'Profile','StaffRow':'Profile',
'KitsugiReviewCard':'Profile','TopicCard':'Comment','ActivityCard':'Comment',
'StaffCharacterRoleCard':'Profile','StaffMediaWorkRow':'Film','StudioMediaGridItem':'Film',
'CastChip':'Profile','BangumiFavoriteRow':'Heart','MalProfileContent':'Profile',
'MalActivityCard':'Comment','ProfileActivityRow':'Comment','UserProfileHeaderCard':'Profile',
'AniListSocialTab':'Profile','UserProfileSocialTab':'Profile','SidebarProfileItem':'Profile',
'TvCharacterCard':'Profile','TvStaffCard':'Profile','TvReviewCard':'Feedback',
}

def main():
    import argparse
    ap=argparse.ArgumentParser();ap.add_argument('inventory');args=ap.parse_args()
    grouped=defaultdict(list)
    for r in json.loads(Path(args.inventory).read_text()):
        if r['status']=='visual' and r['evidence']=='AsyncImage(':grouped[r['path']].append(r)
    count=0
    for rel, rows in grouped.items():
        path=ROOT/rel;data=path.read_bytes();nodes={}
        for n in walk(PARSER.parse(data).root_node):
            if n.type=='call_expression' and (n.start_byte not in nodes or n.end_byte>nodes[n.start_byte].end_byte):nodes[n.start_byte]=n
        edits=[];wrapper=False
        for r in rows:
            if data[r['start']:r['end']].decode()!=r['code']:raise ValueError('Stale inventory: '+rel)
            node=nodes[r['start']]
            if r['name']=='AsyncImage':
                _,args,_=parts(node,data)
                modifier=next(v for v in args.named_children if v.type=='value_argument' and text(v.named_children[0],data)=='modifier')
                # Hoist the entire modifier onto the outer Box. Size/focus/click/semantics
                # stay on the original target; the image fills exactly that same box.
                value=modifier.named_children[-1]
                mod=text(value,data)
                inner=data[node.start_byte:node.end_byte]
                a=value.start_byte-node.start_byte;b=value.end_byte-node.start_byte
                inner=inner[:a]+b'androidx.compose.ui.Modifier.matchParentSize()'+inner[b:]
                show='showMark = !avatarUrl.isNullOrBlank(), ' if 'avatarUrl' in r['code'] else ''
                replacement=('KitsugiArtworkAction(KitsugiGlyphs.Zoom, '+show+'modifier = '+mod+') {\n').encode()+inner+b'\n}'
                edits.append((node.start_byte,node.end_byte,replacement));wrapper=True
            else:
                glyph=FUNCTIONS.get(r['function'])
                if not glyph:raise ValueError('Unreviewed artwork control: '+str(r))
                calls=[n for n in walk(node) if n.type=='call_expression' and parts(n,data) and parts(n,data)[0]=='Text']
                if not calls:
                    print('Delegated artwork:',rel,r['function']);continue
                target=calls[0];_,args,_=parts(target,data)
                edits.append((target.start_byte,args.start_byte+1,('KitsugiMarkedText(KitsugiGlyphs.'+glyph+', ').encode()))
        for a,b,c in sorted(edits,reverse=True):data=data[:a]+c+data[b:]
        if edits:
            s=add_import(data.decode())
            for name in ['KitsugiMarkedText']+(['KitsugiArtworkAction'] if wrapper else []):
                imp='import com.kitsugi.animelist.ui.components.'+name
                if imp not in s:s=s.replace('import com.kitsugi.animelist.ui.theme.icons.KitsugiGlyphs',imp+'\nimport com.kitsugi.animelist.ui.theme.icons.KitsugiGlyphs')
            path.write_text(s);count+=len(edits)
    print('Artwork targets marked:',count)

if __name__=='__main__':main()
