# Projekt „Elias“ – osobní hlasový asistent pro Android

> Původní specifikace projektu od autora (Olda). Tento dokument je zdroj pravdy pro rozsah a pravidla.
> Změny specifikace se dělají jen po domluvě a zapisují se do sekce [Změny specifikace](#změny-specifikace) na konci.

Ahoj, budeme spolu postupně stavět Android aplikaci **Elias**: můj osobní hlasový asistent ve stylu Jarvise. Každé ráno mi nahlas řekne, co se stalo přes noc a co mě dnes čeká. Večer mi řekne, co mám ještě udělat a co je zítra.

Jsem student magisterského oboru Softwarové a informační systémy (ZČU Plzeň). Umím Javu, C#, React a C. Kotlin a Android se teprve učím. Projekt chci mít jako ukázku v GitHub portfoliu, takže kód musí být čistý, otestovaný a musím mu rozumět. Nedělej všechno najednou.

---

## Proč „Elias“

- **Zkratka:** *Everyday Life Intelligent Assistant & Scheduler*, česky „Elektronický Lehký Inteligentní Asistent Studenta“.
- **Biblický odkaz:** v 1 Kr 19,11–12 Bůh promluvil k proroku Eliášovi ne ve vichřici, zemětřesení ani v ohni, ale v „hlasu tichého vánku“. Elias má být stejný: nekřičí, neruší a neplní den hlukem. Ráno a večer tiše a věcně řekne, co je opravdu důležité.
- To se promítá do návrhu: krátké souhrny, žádné zbytečné notifikace, v sabat ticho.
- Do README dej sekci **About the name** s tímto vysvětlením (anglicky). Repozitář pojmenuj `elias-assistant`.

---

## Jak se mnou pracuj

1. **Nejdřív plán, pak kód.** Než začneš, ulož tuhle specifikaci do `docs/SPEC.md` a vytvoř `CLAUDE.md` se stručnými pravidly projektu (architektura, konvence, jak spustit testy). Pak mi napiš plán prvního milníku a počkej na schválení.
2. **Jeden milník najednou.** Po každém milníku se zastav a česky mi napiš:
   - co jsi udělal a proč (krátce, ať se z toho učím),
   - co mám otestovat na telefonu a jak,
   - co je známé omezení nebo dluh.
   Na další milník přejdi, až potvrdím, že funguje.
3. **Commit po každém logickém kroku**, srozumitelné commit zprávy anglicky.
4. **Nehádej API.** U Android API, Claude API, Open-Meteo a RSS si ověř aktuální dokumentaci a verze knihoven. Používej aktuální stabilní verze, ne zastaralé.
5. **Ptej se**, když je rozhodnutí nejasné nebo drahé na změnu. U drobností rozhodni sám a napiš proč.
6. **Kód a komentáře anglicky**, UI, hlas a zprávy pro mě česky. README anglicky s krátkou českou sekcí.

---

## Technologie

- Kotlin, Jetpack Compose (Material 3), minSdk 29, targetSdk = aktuální stabilní
- Architektura: MVVM + oddělené vrstvy `data` (zdroje), `domain` (skládání souhrnu), `ui`
- DI: Hilt
- Síť: Ktor nebo OkHttp + kotlinx.serialization (vyber a zdůvodni)
- Lokální data: Room (log notifikací, cache dat, log běhů), DataStore (nastavení)
- Plánování: AlarmManager (`setAlarmClock`) pro přesné časy, WorkManager pro předběžné stahování
- Hlas: vestavěný Android `TextToSpeech`, jazyk `cs-CZ`
- Čas: výhradně `java.time` se zónou `Europe/Prague`
- Testy: JUnit + MockK (nebo podobné), CI přes GitHub Actions (build + unit testy na každý push)

---

## Osobnost asistenta

- Jmenuje se **Elias**, oslovuje mě **„Oldo“**.
- Mluví česky, věcně, přátelsky a stručně. Nechválí, nemoralizuje a nedramatizuje.
- Text je **pro poslech, ne pro čtení**: žádné odrážky ani zkratky, časy slovy („ve čtvrt na jedenáct“), data přirozeně („v úterý sedmadvacátého října“).
- Ranní souhrn má **120–180 slov** (asi minuta mluvení), večerní **60–100 slov**.

---

## Zdroje dat

| Zdroj | Jak | Poznámka |
|---|---|---|
| Kalendář | `CalendarContract` (tabulka `Instances` kvůli opakovaným událostem), oprávnění `READ_CALENDAR` | Google Kalendář synchronizovaný v telefonu. Pozor: celodenní události jsou v UTC. V nastavení výběr kalendářů. |
| Počasí | Open-Meteo, Plzeň (49.7475, 13.3776), `timezone=Europe/Prague` | Teplota ráno/odpoledne, srážky, vítr. Bez klíče. |
| Zprávy ze světa | RSS: ČT24, iROZHLAS, Root.cz (ověř aktuální URL) | Seznam zdrojů v nastavení. Jen titulky od posledního souhrnu, deduplikace. |
| Můj plán | `assets/plan.json` (viz níže) | Struktura obecná podle rozsahů dat, abych mohl plán později vyměnit. |
| Zprávy přes noc | `NotificationListenerService` (milník 5) | Jen aplikace, odesílatel nebo název konverzace, čas. **Nikdy obsah zprávy.** |
| Západ slunce | výpočet (NOAA algoritmus) pro Plzeň | Kvůli sabatu |

### Struktura `plan.json`

Plán je obecný a řízený rozsahy dat, aby šel později vyměnit za jiný. Obsahuje:

- **Fáze** s názvem a rozsahem dat
- **Minimum dne** (seznam bodů)
- **Pravidla** (seznam bodů)
- **Témata týdne** (týden začíná pondělím): název, popis a konkrétní úkol
- **Ranní čtení** podle data (denně jedna kapitola, i v sobotu)
- **Sobotní studium Bible** podle data
- **Opakované body** podle dne v týdnu (např. „denně kromě soboty“, „út, čt, ne“) a jednorázové body s datem
- **Termíny** (datum, předmět, popis, povinnost) a **revize plánu**

> **Soukromí:** skutečný osobní plán (19. 10. – 30. 11. 2026) je v `docs/private/` a v `app/src/main/assets/plan.json`.
> Oba soubory jsou v `.gitignore`. Veřejné repo obsahuje jen vymyšlený vzorový plán, který použije CI a demo režim.

---

## Pravidla pro sabat (důležité)

- Sabat trvá od **západu slunce v pátek** do **západu slunce v sobotu**.
- **Pátek odpoledne, hodinu před západem slunce:** krátká připomínka „vstup do sabatu“ (úklid, starosti napsat na papír, nedokončené věci do úkolů na neděli, modlitba, při západu vypnout telefon, Žalm 92).
- **Sobota:** žádný ranní hlasový souhrn. Budík (od milníku 6) jen zazvoní, nemluví.
- **Sobota po západu slunce:** krátký souhrn „sabat skončil“: co si odnáším ze studia (otázka), zítra revize cílů a plán týdne.
- V pátek večer žádný večerní souhrn.

---

## Obsah souhrnů

**Ranní (pevné pořadí):**
1. Pozdrav, den a datum, počasí v Plzni
2. Zprávy přes noc (od milníku 5): kdo a kde psal, seskupeno
3. Termíny a události z kalendáře na dnešek
4. Plán: fáze, minimum dne, ranní čtení, dnešní opakované body, téma týdne s konkrétním úkolem
5. Dva až tři titulky ze světa
6. Případné upozornění (termín dnes nebo zítra)

**Večerní:**
1. Odškrtnout dnešek v checklistu, zapsat výdaje, vybrat zítřejší hlavní úkol
2. Zítřejší události a termíny
3. Jedna reflexní otázka k tématu týdne
4. Telefon pryč, spát do půlnoci

---

## AI část (Claude API)

- Endpoint `POST https://api.anthropic.com/v1/messages`, hlavičky `x-api-key`, `anthropic-version`, `content-type`. Ověř v dokumentaci (docs.claude.com).
- Model konfigurovatelný v nastavení. Výchozí nejlevnější aktuální model řady Haiku (ověř aktuální ID modelu v dokumentaci, nehardcoduj ho napevno bez kontroly).
- AI dostane data jako **strukturovaný JSON** a systémový prompt s osobností Eliase a pravidly:
  - používej jen data ze vstupu, nic nevymýšlej,
  - dodrž pořadí a délku,
  - piš pro poslech.
- `max_tokens` rozumně nízko, nízká teplota.
- **Zprávy přes noc do AI neposílej.** Tuhle část skládej lokálně šablonou a vlož ji do textu až po odpovědi AI. Jména lidí tak neopustí telefon.
- **API klíč** v `local.properties` → `BuildConfig`, soubor v `.gitignore`. V README upozorni, že APK s klíčem se nesmí sdílet, a doporuč nastavit limit útraty v Anthropic Console.

---

## Spolehlivost (povinné)

- **AI je vylepšení, ne podmínka.** `TemplateBriefingBuilder` musí umět poskládat použitelný souhrn bez AI. Když API selže nebo vyprší timeout (např. 15 s), použij šablonu.
- **Předběžné stažení dat** 30 minut před ranním souhrnem (WorkManager) a uložení do cache. Ráno už se na síť nečeká.
- **Každý zdroj zvlášť** s timeoutem, opakováním a fallbackem na poslední cache. Selhání jednoho zdroje nesmí shodit souhrn.
- **Přesné časy přes `setAlarmClock()`.** Znovunaplánovat po `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` a po aktualizaci aplikace.
- **Přehrávání ve foreground service** (typ `mediaPlayback`), audio focus (ztlumit hudbu, při hovoru pauza), notifikace s tlačítky Pauza / Znovu / Stop.
- **Budík (milník 6) musí zazvonit vždy**, i bez sítě a bez TTS. Zvuk budíku je nezávislý na zbytku.
- **Kontroly oprávnění** při startu a obrazovka „Stav“: kalendář, notifikace (Android 13+), přesné budíky (Android 14+), čtení oznámení, výjimka z optimalizace baterie, nainstalovaný český hlas. U každého tlačítko „Opravit“, které otevře správné nastavení. Odkaz na dontkillmyapp.com pro můj telefon.
- **Log běhů v Roomu:** čas, které zdroje se povedly, které selhaly a proč, jestli se použila AI, nebo šablona. Obrazovka „Log“.
- **Změna času (letní/zimní)** a celodenní události pokryté testy.

---

## Soukromí

- Notifikace: ukládat jen balíček aplikace, titulek (odesílatel nebo konverzace) a čas. Ignorovat souhrnné notifikace (`FLAG_GROUP_SUMMARY`), probíhající notifikace, vlastní aplikaci a systém.
- Seskupovat podle konverzace („ve skupině Mládež dvanáct zpráv“).
- Automatické mazání záznamů starších 48 hodin.
- V nastavení seznam sledovaných aplikací (whitelist).
- V README sekce **Privacy** s popisem, co se ukládá a co opouští telefon.

---

## Plány a deník (M4.5)

**Plány podle témat**

- Plány na další dny píšu do Google Kalendáře. Každé téma má vlastní kalendář (např. Škola, Seberozvoj, Španělština).
  V Eliasovi označím, které kalendáře jsou témata. Ostatní kalendáře jsou běžné události.
- U každého kalendáře jde v Plánech vypnout **čtení** (třeba Narozeniny nebo Svátky), u každého účtu jedním
  přepínačem **Číst vše**. Kalendáře jsou seskupené podle účtů v telefonu (Google účty, účet Samsung, jen v telefonu).
  Vypnutý kalendář Elias úplně vynechá: v souhrnech, v Plánech, na obrazovce Data i v datech pro AI. Nový kalendář se
  čte, dokud ho nevypnu.
- Stejná událost ve více kalendářích (stejný název a čas, třeba ve dvou rodinných kalendářích) se ukáže a přečte jen
  jednou. Když je jedna z kopií v kalendáři tématu, zůstane ta.
- Obrazovka **Plány** ukáže sedm dní dopředu, každý den seskupený podle témat. Každá položka má barvu svého kalendáře
  a čas ve vlastním sloupci, témata a další události odděluje čára. Plány se píšou rovnou v Google Kalendáři, Elias do
  kalendáře sám nezapisuje (žádné oprávnění `WRITE_CALENDAR`).
- Ranní souhrn: nejdřív události s časem, pak plány podle témat. Večerní souhrn: zítřek podle témat a jedna věcná věta,
  když některé téma na zítřek nemá nic.

**Deník**

- Zápis se píše v aplikaci, jeden na den: **myšlenka dne**, **co mi utkvělo**, **na čem se zlepšit**. Zápis před
  čtvrtou ráno patří ještě k předchozímu dni.
- Obrazovka **Deník**: dnešní zápis a historie starších zápisů, které jdou otevřít a upravit.
- **Krok od AI:** tlačítko „Navrhni krok“ pošle do AI jen pole „na čem se zlepšit“ a téma týdne. Myšlenka dne a co mi
  utkvělo telefon nikdy neopustí. Navržený krok jde upravit.
- **Večer:** když dnešní zápis chybí, večerní souhrn připomene, ať si ho zapíšu. Žádná další notifikace.
- **Ráno:** ranní souhrn připomene včerejší krok (nebo „na čem se zlepšit“, když krok chybí).
- Části souhrnu o deníku skládá šablona v telefonu a vkládají se až po odpovědi AI, stejně jako zprávy přes noc.
  Obsah deníku se do AI pro souhrn nikdy neposílá.
- Deník je jen v telefonu (Room, bez zálohy). Do logu běhů se nezapisuje.

---

## Milníky

Ke každému milníku: testy, aktualizace README a shrnutí pro mě.

**M0 – Základ:** projekt, Compose, Hilt, struktura balíčků, `local.properties` pro klíč, GitHub Actions (build + testy), README kostra, `docs/SPEC.md`, `CLAUDE.md`.
✅ Hotovo, když: projekt jde sestavit, CI je zelené, aplikace se spustí s prázdnou obrazovkou.

**M1 – Zdroje dat:** kalendář, počasí, RSS, `plan.json`, výpočet západu slunce. Obrazovka „Data“ zobrazí, co by Elias dnes věděl.
✅ Hotovo, když: na telefonu vidím dnešní události, počasí, titulky a plán dne. Unit testy pro parsery, západ slunce, celodenní události a změnu času.

**M2 – Souhrn:** `TemplateBriefingBuilder` (bez AI) + `ClaudeBriefingBuilder` + výběr s fallbackem. Obrazovka „Náhled textu“.
✅ Hotovo, když: vidím ranní i večerní text z AI i ze šablony, a při vypnuté síti nebo špatném klíči se použije šablona.

**M3 – Hlas:** TTS ve foreground service, audio focus, ovládání v notifikaci, kontrola českého hlasu, nastavení rychlosti. Tlačítko „Přehrát ranní / večerní souhrn“.
✅ Hotovo, když: Elias nahlas přečte souhrn, při hovoru se zastaví a jde ho pauznout z notifikace.

**M4 – Plánování:** předběžné stažení dat, automatický večerní souhrn (notifikace + přehrání), páteční připomínka sabatu, sobotní režim, znovunaplánování po restartu, obrazovka „Stav“ s oprávněními.
✅ Hotovo, když: večerní souhrn se sám spustí v nastavený čas i po restartu telefonu, v pátek a v sobotu platí pravidla sabatu.

**M4.5 – Plány a deník:** plány na další dny podle témat z Google Kalendáře (kalendář pro každé téma), obrazovka
„Plány“ na sedm dní dopředu, souhrny seskupené podle témat, deník v aplikaci (myšlenka dne, co mi utkvělo, na čem se
zlepšit) s historií, krok navržený AI, večerní připomínka zápisu a ranní připomenutí včerejšího kroku.
Podrobnosti v sekci [Plány a deník](#plány-a-deník-m45).
✅ Hotovo, když: v Plánech vidím příští dny podle témat, Elias je ráno i večer čte po tématech, zapíšu deník, AI navrhne
krok, večer mi Elias připomene chybějící zápis a ráno včerejší krok.

**M5 – Zprávy přes noc:** NotificationListenerService, filtry, seskupení, Room, mazání po 48 h, whitelist aplikací, lokální šablona.
✅ Hotovo, když: ráno uslyším, kdo a kde mi v noci psal, bez obsahu zpráv a bez spamu ze skupin.

**M6 – Budík:** vlastní budík v aplikaci. Zazvoní, po vypnutí Elias mluví. Odložení (snooze). Záložní zvuk, když cokoli selže. V sobotu bez mluvení.
✅ Hotovo, když: budík zazvoní i v režimu letadlo a po restartu telefonu.

**M7 – Dotažení:** nastavení (časy, zdroje, kalendáře, aplikace, model, hlas), widget „Přehrát“, demo režim s falešnými daty, obrazovka „Log“, README s diagramem architektury, screenshoty a sekcí Privacy.
✅ Hotovo, když: projekt je připravený ukázat v portfoliu.

**Později (nápady, nedělat bez domluvy):** hlasové otázky („Eliasi, co mám zítra?“) přes rozpoznávání řeči, propojení s Google Tasks, hlídání změn na stránkách předmětů.

---

## Změny specifikace

- **2026-10-27:** Repo je veřejné. Osobní plán byl přesunut ze specifikace do `docs/private/plan-2026-podzim.md` (mimo git). Ve specifikaci zůstala jen jeho struktura.
- **2026-10-27:** Kořenový balíček a applicationId: `io.github.adccz.elias`.
- **2026-10-27 (M2):** Parametr `temperature` (i `top_p`, `top_k`) API od verze `2026-10-21` odmítá u všech modelů. „Nízkou teplotu“ nahrazuje `output_config.effort: "low"`, odpověď ve struktuře JSON (structured outputs) a kontrola výstupu; při problému se použije šablona. Hlavička `anthropic-version: 2026-10-21`.
- **2026-10-27 (M2):** Výchozí model `claude-haiku-5-5` (Claude Haiku 5.5).
- **2026-10-27 (M2):** Téma týdne v `plan.json` má nepovinné pole `reflectionQuestion` pro večerní otázku. Když chybí, otázku složí šablona.
- **2026-10-27:** Jméno se na obrazovce a v README píše **E.L.I.A.S.**, aby bylo jasné, že jde o zkratku. Nahlas se vyslovuje „Elias“ (hlasová syntéza by tečkovanou podobu hláskovala). V kódu zůstává `Elias`.
- **2026-10-29 (M4):** Ranní a večerní čas se nastavuje zvlášť pro každý den v týdnu (obrazovka Časy), výchozí 7:00
  a 22:00, každý jde vypnout. Zvonící budík zůstává v M6.
- **2026-10-29 (M4):** Předběžné stažení dat spouští přesný budík 30 minut před ranním souhrnem a práci dělá WorkManager
  (expedited). Samotný WorkManager by v noci v režimu Doze mohl čekat hodiny.
- **2026-10-29 (M4):** Automatický večerní souhrn rozsvítí obrazovku přes zamčený telefon (celoobrazovková notifikace jako
  u budíku) a hned mluví. Když se telefon zrovna používá, zazní jen zvuk.
- **2026-10-29 (M4):** Páteční připomínka sabatu je jen notifikace. „Sabat skončil“ je začátek sobotního večerního
  souhrnu, nikdy před západem slunce, a nahrazuje v něm pozdrav s checklistem.
- **2026-10-29 (M4):** Titulky „od posledního souhrnu“ znamenají od posledního přehraného souhrnu, nejméně 6 a nejvýš
  24 hodin zpět.
- **2026-10-29 (hlas):** Výchozí hlas je vlastní mužský offline hlas „Oldřich 3.0“ (Piper „Jirka“ přes sherpa-onnx,
  model přibalený v APK). Vestavěný Android `TextToSpeech` zůstává jako záloha a jde zvolit v Náhledu textu.
- **2026-10-29 (M4.5):** Nový milník M4.5 „Plány a deník“ mezi M4 a M5 (sekce [Plány a deník](#plány-a-deník-m45)).
  Plány podle témat se čtou z Google Kalendáře, kde má každé téma vlastní kalendář. Deník se píše v aplikaci. AI dostane
  jen pole „na čem se zlepšit“ a téma týdne, a to jen po klepnutí na „Navrhni krok“.
- **2026-10-29 (M4.5):** Výběr kalendářů ze sekce M7 je dřív: v Plánech jde u každého kalendáře vypnout čtení.
  Vypnutý kalendář Elias nikde nečte a nemůže být téma.
- **2026-10-29 (M4.5):** Stejná událost ve více kalendářích se ukáže a přečte jen jednou (přání uživatele).
- **2026-10-29 (M4.5):** Po testu na telefonu: kalendáře v Plánech seskupené podle účtů s přepínačem „Číst vše“,
  položky dnů vizuálně oddělené a bez tlačítka „Přidat do kalendáře“ (bylo zbytečné).
