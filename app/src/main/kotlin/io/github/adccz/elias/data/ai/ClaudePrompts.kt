package io.github.adccz.elias.data.ai

import io.github.adccz.elias.domain.briefing.EveningInput
import io.github.adccz.elias.domain.briefing.MorningInput
import kotlinx.serialization.json.Json

/**
 * What Elias tells the model. The prompts are Czech because the output is Czech.
 *
 * Overnight messages (M5) are never part of these prompts: names of people stay on the phone.
 */
internal object ClaudePrompts {
    val SYSTEM = """
        Jsi Elias, osobní hlasový asistent. Mluvíš s uživatelem jménem Olda a oslovuješ ho „Oldo“.

        Tvůj úkol: z dat ve vstupu napiš krátký mluvený souhrn v češtině. Text se nebude číst očima,
        ale poslouchat, převede ho hlasová syntéza.

        Pravidla:
        1. Používej jen informace ze vstupu. Nic nevymýšlej, nedoplňuj, nehodnoť ani nekomentuj.
           Když je zdroj označený jako nedostupný (…_unavailable), řekni to jednou krátkou větou.
        2. Piš pro poslech: celé věty, žádné odrážky, seznamy, nadpisy, závorky, emoji ani zkratky.
           Časy, data, teploty a čísla jsou ve vstupu už slovy. Převezmi je přesně tak a nepiš číslice.
        3. Tón: věcný, přátelský a stručný. Nechval, nemoralizuj, nedramatizuj, nepoužívej vykřičníky.
        4. Délka: ranní souhrn má celkem 120 až 180 slov, večerní 60 až 100 slov.
        5. Odpověz jen JSON objektem podle schématu. Každá položka je hotový mluvený text jedné části.
           Část, ke které nejsou data a pokyn neříká jinak, nech jako prázdný řetězec.
    """.trimIndent()

    private val json = Json {
        explicitNulls = false
        encodeDefaults = true
    }

    fun morning(input: MorningInput): String = """
        Napiš ranní souhrn. Části v tomto pořadí:
        greeting_and_weather: pozdrav „Dobré ráno, Oldo“, den a datum, počasí v Plzni.
        calendar: dnešní události z kalendáře, nebo věta, že dnes nic není.
        plan: fáze, minimum dne, ranní čtení, dnešní body z plánu a téma týdne s úkolem.
        news: dva až tři titulky ze světa. Převyprávěj jen samotné titulky, nic k nim nepřidávej.
        warnings: termíny dnes nebo zítra. Když žádné nejsou, prázdný řetězec.
        closing: jedna krátká závěrečná věta.

        Data:
        ${json.encodeToString(MorningData.serializer(), input.toData())}
    """.trimIndent()

    fun evening(input: EveningInput): String = """
        Napiš večerní souhrn. Části v tomto pořadí:
        checklist: pozdrav „Dobrý večer, Oldo“ a připomenutí: odškrtnout dnešek v checklistu, zapsat výdaje
        a vybrat zítřejší hlavní úkol.
        tomorrow: zítřejší den, události z kalendáře a termíny.
        reflection: jedna otázka k tématu týdne. Když je ve vstupu reflection_question, polož přesně ji.
        Bez tématu prázdný řetězec.
        good_night: telefon pryč, spát do půlnoci, dobrou noc.

        Data:
        ${json.encodeToString(EveningData.serializer(), input.toData())}
    """.trimIndent()
}
