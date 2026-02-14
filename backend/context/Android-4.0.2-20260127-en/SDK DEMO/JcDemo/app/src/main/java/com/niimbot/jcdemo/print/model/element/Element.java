package com.niimbot.jcdemo.print.model.element;

public abstract class Element {
    private String type;

    /**
     * Constructor with type parameter
     * @param type Element type
     * @throws IllegalArgumentException If type is null or empty string
     */
    public Element(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Element type cannot be null or empty");
        }
        this.type = type;
    }

    public String getType() { return type; }
    
    public void setType(String type) { 
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Element type cannot be null or empty");
        }
        this.type = type; 
    }
}
