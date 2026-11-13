package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterSearchTest {
    private val items = listOf(
        SearchableCharacter("1", "Animada", "Una chica alegre", listOf("Romance", "anime")),
        SearchableCharacter("2", "Brain-parasite", "Un gusano parásito", listOf("terror")),
        SearchableCharacter("3", "Psicólogo Digital", "Terapia con IA", listOf("Anime")),
    )

    @Test fun emptyQueryAndTagKeepsAll() = assertEquals(listOf("1", "2", "3"), CharacterSearch.filterIds(items, "  ", null))

    @Test fun searchesNameDescriptionAndTags() {
        assertEquals(listOf("1"), CharacterSearch.filterIds(items, "animada", null))
        assertEquals(listOf("2"), CharacterSearch.filterIds(items, "GUSANO", null))
        assertEquals(listOf("2"), CharacterSearch.filterIds(items, "terror", null))
    }

    @Test fun everyWordMustAppear() {
        assertEquals(listOf("1"), CharacterSearch.filterIds(items, "chica alegre", null))
        assertEquals(emptyList<String>(), CharacterSearch.filterIds(items, "chica gusano", null))
    }

    @Test fun tagFilterIgnoresCase() {
        assertEquals(listOf("1", "3"), CharacterSearch.filterIds(items, "", "ANIME"))
        assertEquals(listOf("3"), CharacterSearch.filterIds(items, "terapia", "anime"))
    }

    @Test fun blankTagMeansNoFilter() = assertEquals(3, CharacterSearch.filterIds(items, "", "  ").size)

    @Test fun allTagsDedupedAndSorted() {
        assertEquals(listOf("anime", "Romance", "terror"), CharacterSearch.allTags(items))
    }
}
