#!/usr/bin/env python3
"""Add explicit, semantic icons to simple text-only buttons. Safe to rerun.
No runtime label matching: all mappings are applied to source at generation time.
"""
from audit_controls import SRC, controls
from migrate_material_icons import add_import

GROUPS = {
'Close': 'iptal kapat settings_cancel action_close vazgec action_cancel iptal_et pd_cancel pf_err_close inline_discard_14d2482 inline_close_and_don_t_show_38bb57d hayir fandom_confirm_no',
'Refresh': 'action_retry_label action_retry yenile sifirla inline_reset_8fb7f0b yeniden_dene notif_mal_load_more_retry studio_page_load_more_failed fandom_retry explore_source_retry player_btn_retry about_release_notes_retry wiki_reading_reset',
'Save': 'kaydet action_save',
'Delete': 'action_delete inline_delete_entire_list_174ee79 inline_remove_2b5bd53',
'Check': 'action_ok settings_ok action_yes search_dialog_ok onayla tamamlandi fandom_confirm_yes',
'Back': 'geri player_btn_back',
'Tune': 'search_filter_apply settings_custom_color_apply action_apply wiki_reading_apply',
'Search': 'source_searching manga_find_mirror',
'Queue': 'listeye_ekle evet_ekle',
'Rows': 'tumunu_gor inline_show_results_f77b61a stream_all_sources',
'Copy': 'ui_action_copied',
'Upload': 'ice_aktar inline_import_from_json_1572af3',
'HeartOutline': 'action_favourite_remove',
'External': 'kaynakta_ac inline_sign_in_with_tv_browser_8345c87 inline_my_shikimori_oauth_apps_05c1e76 inline_copy_code_open_browser_b49a9b4 fandom_reader_open_browser inline_open_in_browser_eed20ab player_btn_external',
'Shield': 'saglik_testi inline_accept_and_enable_03bffee inline_keep_accounts_on_this_device_fd2ebcf',
'Cleaning': 'domain_temizle ua_temizle inline_clear_saved_keys_2e9198b raporu_temizle temizle inline_reset_statistics_46f0f93',
'Key': 'sifreyi_degistir sifreyi_dogrula_ve_kasayi_ac',
'Clock': 'loading daha_sonra account_resend_countdown',
'ChevronUp': 'fandom_show_less explore_source_collapse',
'Eye': 'fandom_spoiler_reveal',
'Feedback': 'fandom_report_wrong inline_send_error_report_to_developer_bc60edd',
'Download': 'guven_ve_yukle',
'Stop': 'cross_sync_action_stop',
'Forward': 'devam_et arka_planda_devam_et explore_source_next',
'Edit': 'duzenle inline_edit_extensions_134b588',
'Plus': 'pd_create ekle',
'Link': 'inline_alternative_one_tap_kitsugi_e45c65e baglan',
'DownloadDone': 'inline_export_as_json_fff9b39',
'Code': 'inline_view_logcat_2bebdd3 inline_view_developer_logs_bebf6dc',
'Image': 'inline_reset_image_location_e352f15',
'Film': 'inline_reset_video_location_d964174',
'Translate': 'translate_show_translation',
'Sync': 'tumunu_guncelle manga_auto_update_check_button inline_sync_now_2f993db inline_check_now_c288ab8',
'CloudSync': 'inline_keep_cloud_accounts_47e2ee5',
'Login': 'inline_sign_out_8d1ab12 ui_account_prompt_sign_in',
'PersonAdd': 'ui_account_sign_up',
'Fullscreen': 'inline_open_fullscreen',
'Play': 'inline_episode_1_s_dc662be simdi_oynat',
'History': 'fandom_reject_undo_action',
'Home': 'fandom_open_wiki_home',
'Book': 'wiki_simple_reader',
'Plugin': 'player_btn_switch_source',
'Bookmark': 'inline_set_as_default_03a9077 inline_make_default_59f76d5',
'SkipNext': 'inline_next_episode_ab03b91',
}
LABELS = {label: glyph for glyph, labels in GROUPS.items() for label in labels.split()}
LABELS.update({'Tekrar Dene':'Refresh', 'Uygula':'Tune', 'Bitti':'Check', 'Rapor':'Feedback',
'Tam Ekran':'Fullscreen', '-':'Minus', '+':'Plus', 'Evet, devam et':'Play',
'Hayır, duraklat':'Pause', 'Dahili Oynatıcı':'Player','Harici Oynatıcı':'External',
'Scripts (Lua)':'Code','Script Options (Conf)':'Tune'})

def main():
    changed = total = 0
    for path in SRC.rglob('*.kt'):
        text = path.read_text()
        edits = []
        for start,end,label in controls(text):
            glyph = LABELS.get(label)
            if glyph is None: continue
            # Preserve existing multiline indentation, including one-line dialogs.
            body = text[start:end]
            indent = body[:len(body)-len(body.lstrip())]
            edits.append((start, indent + 'KitsugiActionMark(KitsugiGlyphs.' + glyph + ')\n'))
        if not edits: continue
        for start, insertion in reversed(edits):
            text = text[:start] + insertion + text[start:]
        text = add_import(text)
        imp = 'import com.kitsugi.animelist.ui.components.KitsugiActionMark'
        if imp not in text:
            text = text.replace('import com.kitsugi.animelist.ui.theme.icons.KitsugiGlyphs', imp+'\nimport com.kitsugi.animelist.ui.theme.icons.KitsugiGlyphs')
        path.write_text(text)
        changed += 1
        total += len(edits)
    print(f'{total} text-only buttons updated in {changed} files')

if __name__ == '__main__': main()
