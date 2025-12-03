package org.fuh.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CategoryBlock {
    private final String name;
    private final Set<String> categories;

    public CategoryBlock(String name, List<String> categoriesList) {
        this.name = name;
        // Usamos un Set para búsqueda rápida (O(1))
        this.categories = new HashSet<>(categoriesList);
    }

    public String getName() { return name; }

    // Verifica si una categoría pertenece a este bloque
    public boolean contains(String category) {
        return categories.contains(category);
    }

    // Verifica si DOS categorías pertenecen AMBAS a este bloque
    // Esto es lo que usará el algoritmo para saber si suma puntos
    public boolean match(String cat1, String cat2) {
        return categories.contains(cat1) && categories.contains(cat2);
    }
    
    @Override
    public String toString() {
        return name + ": " + categories;
    }
}