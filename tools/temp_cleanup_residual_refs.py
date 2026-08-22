from pathlib import Path
import re


def update(path: str, replacements: list[tuple[str, str]]) -> None:
    p = Path(path)
    text = p.read_text(encoding='utf-8')
    for old, new in replacements:
        text = text.replace(old, new)
    p.write_text(text, encoding='utf-8')


# Remove the retired third automation source from the old interoperability research notes.
update('BRIDGE_RESEARCH_NOTES.md', [
    ('third-party apps (Even Hub, MentraOS, external automation)', 'third-party app runtimes (Even Hub and MentraOS)'),
    ('Select an Even Hub / Mentra / external automation app', 'Select an Even Hub or Mentra app'),
    ('│   Mentra /      │     │   mapping +  │     │  (RFCOMM →      │\n│   external automation)       │     │   routing)   │     │   glasses)      │',
     '│   Mentra)       │     │   mapping +  │     │  (RFCOMM →      │\n│                 │     │   routing)   │     │   glasses)      │'),
    ('`EvenHubPluginSource`, `MentraPluginSource`, `external automationPluginSource`', '`EvenHubPluginSource`, `MentraPluginSource`'),
    ('source filter chips (All / Even Hub / Mentra / external automation)', 'source filter chips (All / Even Hub / Mentra)'),
])

update('android/AD-Glasses/LOCAL_AGENT_MVP_PLAN.md', [
    ('The feature works without external automation or AutoInput.', 'The feature is implemented within AD Glasses and requires no third-party automation bridge.'),
])

# These IDs are live generic external-plugin-source UI. Keep the IDs but correct old
# translations that still named the retired automation product. Remove the dead localized
# assistant-mode/setup strings, which have no callers and no base value.
locale_replacements = {
    'values-de': (
        'In external automation öffnen', 'Externe Quelle öffnen',
        'external automationNet-Link *', 'Link zur externen Quelle *',
        'Geben Sie die external automationNet-URL Ihres Profils ein.', 'Geben Sie die URL der externen Quelle für Ihr Plugin ein.',
    ),
    'values-es': (
        'Abrir en external automation', 'Abrir fuente externa',
        'Enlace de external automationNet *', 'Enlace de fuente externa *',
        'Introduce la URL de external automationNet de tu perfil.', 'Introduce la URL de la fuente externa de tu plugin.',
    ),
    'values-fr': (
        'Ouvrir dans external automation', 'Ouvrir la source externe',
        'Lien external automationNet *', 'Lien de source externe *',
        "Saisissez l'URL external automationNet de votre profil.", "Saisissez l'URL de la source externe de votre plugin.",
    ),
    'values-it': (
        'Apri in external automation', 'Apri fonte esterna',
        'Link external automationNet *', 'Link sorgente esterna *',
        "Inserisci l'URL external automationNet del tuo profilo.", "Inserisci l'URL della sorgente esterna per il tuo plugin.",
    ),
    'values-ko': (
        'external automation에서 열기', '외부 소스 열기',
        'external automationNet 링크 *', '외부 소스 링크 *',
        '프로필의 external automationNet URL을 입력하세요.', '플러그인의 외부 소스 URL을 입력하세요.',
    ),
    'values-pt-rBR': (
        'Abrir no external automation', 'Abrir fonte externa',
        'Link do external automationNet *', 'Link da fonte externa *',
        'Digite a URL do external automationNet para seu perfil.', 'Digite a URL da fonte externa do seu plugin.',
    ),
    'values-ru': (
        'Открыть в external automation', 'Открыть внешний источник',
        'Ссылка external automationNet *', 'Ссылка на внешний источник *',
        'Введите URL профиля external automationNet.', 'Введите URL внешнего источника для плагина.',
    ),
    'values-zh-rCN': (
        '在 external automation 中打开', '打开外部来源',
        'external automationNet 链接 *', '外部来源链接 *',
        '输入你的 external automationNet 配置文件网址。', '输入插件的外部来源 URL。',
    ),
}

root = Path('android/AD-Glasses/shared/src/commonMain/composeResources')
for locale, values in locale_replacements.items():
    path = root / locale / 'strings_extra.xml'
    text = path.read_text(encoding='utf-8')
    old_open, new_open, old_link, new_link, old_hint, new_hint = values
    text = text.replace(old_open, new_open)
    text = text.replace(old_link, new_link)
    text = text.replace(old_hint, new_hint)
    for resource_name in ('dashboard_phone_assistant', 'dashboard_gemini_chatgpt_setup'):
        text = re.sub(
            rf'^[ \t]*<string name="{resource_name}">.*?</string>[ \t]*\r?\n?',
            '',
            text,
            flags=re.MULTILINE,
        )
    # Repair indentation if the earlier cleanup removed leading spaces from the next resource.
    text = re.sub(r'(?m)^<string name="dashboard_custom_provider">', '    <string name="dashboard_custom_provider">', text)
    path.write_text(text, encoding='utf-8')

print('residual legacy-reference cleanup applied')
