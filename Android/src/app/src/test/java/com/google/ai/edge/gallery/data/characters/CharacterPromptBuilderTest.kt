package com.google.ai.edge.gallery.data.characters

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterPromptBuilderTest {

    private val card = CharacterCard(
        name = "Layla",
        description = "{{char}} es amiga de {{user}}.",
        personality = "Curiosa",
        scenario = "Una cafetería",
        firstMessage = "Hola, {{user}}.",
        alternateGreetings = listOf("Segundo", "Hola, {{user}}.", "  "),
        exampleMessages = "<START>\n{{user}}: Hola\n{{char}}: Buenas",
        postHistoryInstructions = "Mantén a {{char}} en personaje.",
    )

    @Test
    fun replacesMacrosEverywhere() {
        val built = CharacterPromptBuilder.build(card, userName = "Ana")
        assertTrue(built.systemPrompt.contains("Layla es amiga de Ana."))
        assertTrue(built.systemPrompt.contains("Ana: Hola"))
        assertTrue(built.systemPrompt.contains("Layla: Buenas"))
        assertFalse(built.systemPrompt.contains("{{"))
        assertEquals("Mantén a Layla en personaje.", built.postHistoryInstructions)
    }

    @Test
    fun macrosAreCaseInsensitiveAndLegacyTagsWork() {
        assertEquals(
            "A B B A {{random}}",
            CharacterPromptBuilder.applyMacros("{{CHAR}} {{ user }} <USER> <bot> {{random}}", "A", "B"),
        )
    }

    @Test
    fun namesWithSpecialRegexCharactersAreNotMangled() {
        assertEquals("A\$1\\B", CharacterPromptBuilder.applyMacros("{{char}}", "A\$1\\B", "u"))
    }

    @Test
    fun usesDefaultInstructionWhenCardHasNoSystemPrompt() {
        val built = CharacterPromptBuilder.build(card, userName = "Ana")
        assertTrue(built.systemPrompt.contains("Write only Layla's next reply"))
        assertTrue(built.systemPrompt.endsWith("Reply in the language Ana writes in."))
    }

    @Test
    fun customSystemPromptReplacesDefaultAndOriginalMacroExpandsIt() {
        val custom = CharacterPromptBuilder.build(card.copy(systemPrompt = "Solo habla de {{char}}."), "Ana")
        assertTrue(custom.systemPrompt.startsWith("Solo habla de Layla."))
        assertFalse(custom.systemPrompt.contains("Write only Layla's next reply"))

        val withOriginal = CharacterPromptBuilder.build(card.copy(systemPrompt = "Antes. {{original}} Después."), "Ana")
        assertTrue(withOriginal.systemPrompt.startsWith("Antes. Write only Layla's next reply"))
        assertTrue(withOriginal.systemPrompt.contains("Después."))
    }

    @Test
    fun emptySectionsAreOmitted() {
        val built = CharacterPromptBuilder.build(CharacterCard(name = "Solo"))
        assertFalse(built.systemPrompt.contains("Information about"))
        assertFalse(built.systemPrompt.contains("[Example Chat]"))
        assertFalse(built.systemPrompt.contains("impression"))
        assertFalse(built.systemPrompt.contains("personality:"))
        assertFalse(built.systemPrompt.contains("Scenario:"))
        assertEquals(emptyList<String>(), built.greetings)
        assertEquals("", built.postHistoryInstructions)
    }

    @Test
    fun exampleDialogueDropsStartMarkers() {
        val built = CharacterPromptBuilder.build(
            card.copy(exampleMessages = "<start>\n{{user}}: a\n<START>\n{{char}}: b"),
            "Ana",
        )
        assertFalse(built.systemPrompt.contains("<start>", ignoreCase = true))
        assertTrue(built.systemPrompt.contains("[Example Chat]"))
    }

    @Test
    fun greetingsCombineFirstMessageAndAlternatesWithoutBlanksOrDuplicates() {
        val built = CharacterPromptBuilder.build(card, userName = "Ana")
        assertEquals(listOf("Hola, Ana.", "Segundo"), built.greetings)
    }

    @Test
    fun pickGreetingHandlesEmptySingleAndMany() {
        assertNull(CharacterPromptBuilder.pickGreeting(emptyList()))
        assertEquals("unico", CharacterPromptBuilder.pickGreeting(listOf("unico")))
        val many = listOf("a", "b", "c")
        repeat(20) {
            assertTrue(CharacterPromptBuilder.pickGreeting(many, Random(it)) in many)
        }
    }

    @Test
    fun blankNamesFallBackToDefaults() {
        val built = CharacterPromptBuilder.build(CharacterCard(name = "  "), userName = " ")
        assertTrue(built.systemPrompt.contains("Character"))
        assertTrue(built.systemPrompt.contains("User"))
    }

    @Test
    fun personaDescriptionGoesInItsOwnSectionWithMacrosResolved() {
        val built = CharacterPromptBuilder.build(
            card,
            userName = "Ana",
            userDescription = "{{user}} es una exploradora. {{char}} la conoce.",
        )
        assertTrue(built.systemPrompt.contains("Information about Ana: Ana es una exploradora. Layla la conoce."))
        assertFalse(built.systemPrompt.contains("{{"))
    }

    @Test
    fun blankPersonaDescriptionAddsNoSection() {
        val built = CharacterPromptBuilder.build(card, userName = "Ana", userDescription = "   ")
        assertFalse(built.systemPrompt.contains("Information about"))
    }

    @Test
    fun promptIsPlainTextWithSillyTavernLabelsAndNoMarkdown() {
        val built = CharacterPromptBuilder.build(
            card.copy(exampleMessages = "<START>\n{{user}}: Hola\n{{char}}: Buenas"),
            userName = "Ana",
            userDescription = "Exploradora",
        )
        assertFalse(built.systemPrompt.contains("#"))
        assertFalse(built.systemPrompt.contains("**"))
        assertTrue(built.systemPrompt.contains("Layla's personality: Curiosa"))
        assertTrue(built.systemPrompt.contains("Scenario: Una cafetería"))
    }

    @Test
    fun identityLineIsTheLastThingInThePrompt() {
        val built = CharacterPromptBuilder.build(card, userName = "Ana")
        assertTrue(
            built.systemPrompt.contains(
                "The User is Ana. You ARE Layla. Embody the character and personality completely."
            )
        )
        // lo ultimo es la frase de identidad (+ la instruccion corta si no hay prompt propio)
        val idx = built.systemPrompt.indexOf("The User is Ana.")
        assertTrue(idx > built.systemPrompt.indexOf("Una cafetería"))
        assertTrue(idx > built.systemPrompt.indexOf("[Example Chat]"))
    }

    @Test
    fun longCardsAreNotTruncated() {
        val big = card.copy(description = "x".repeat(20_000))
        assertTrue(CharacterPromptBuilder.build(big).systemPrompt.length > 20_000)
    }
}
