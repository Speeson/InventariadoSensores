package com.niimbot.jcdemo.print.model.template;

import com.google.gson.annotations.SerializedName;
import com.niimbot.jcdemo.print.model.element.Element;

import java.util.List;
import java.util.Objects;

/**
 * Print template class for encapsulating all print template data
 * Contains initialization drawing board parameters and element list
 */
public class PrintTemplate {
    @SerializedName("InitDrawingBoardParam")
    private InitDrawingBoardParam initDrawingBoardParam;
    private List<Element> elements;
    
    /**
     * Full parameter constructor
     * @param initDrawingBoardParam Initialization drawing board parameters
     * @param elements Element list
     * @throws IllegalArgumentException If any parameter is null
     */
    public PrintTemplate(InitDrawingBoardParam initDrawingBoardParam, List<Element> elements) {
        if (initDrawingBoardParam == null) {
            throw new IllegalArgumentException("InitDrawingBoardParam cannot be null");
        }
        if (elements == null) {
            throw new IllegalArgumentException("Elements list cannot be null");
        }
        this.initDrawingBoardParam = initDrawingBoardParam;
        this.elements = elements;
    }
    
    /**
     * Get initialization drawing board parameters
     * @return Initialization drawing board parameters
     */
    public InitDrawingBoardParam getInitDrawingBoardParam() { 
        return initDrawingBoardParam; 
    }
    
    /**
     * Set initialization drawing board parameters
     * @param initDrawingBoardParam Initialization drawing board parameters
     * @throws IllegalArgumentException If parameter is null
     */
    public void setInitDrawingBoardParam(InitDrawingBoardParam initDrawingBoardParam) {
        if (initDrawingBoardParam == null) {
            throw new IllegalArgumentException("InitDrawingBoardParam cannot be null");
        }
        this.initDrawingBoardParam = initDrawingBoardParam; 
    }
    
    /**
     * Set element list
     * @param elements Element list
     * @throws IllegalArgumentException If parameter is null
     */
    public void setElements(List<Element> elements) {
        if (elements == null) {
            throw new IllegalArgumentException("Elements list cannot be null");
        }
        this.elements = elements;
    }
    
    /**
     * Get element list
     * @return Element list
     */
    public List<Element> getElements() { 
        return elements; 
    }
    
    /**
     * Add element to template
     * @param element Element to add
     * @throws IllegalArgumentException If element is null
     */
    public void addElement(Element element) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }
        this.elements.add(element);
    }
    
    /**
     * Remove element from template
     * @param element Element to remove
     * @return True if element was successfully removed, false otherwise
     */
    public boolean removeElement(Element element) {
        if (element == null) {
            return false;
        }
        return this.elements.remove(element);
    }
    
    /**
     * Get the number of elements in the template
     * @return Number of elements
     */
    public int getElementCount() {
        return this.elements != null ? this.elements.size() : 0;
    }
    
    /**
     * Check if template is empty (no elements)
     * @return True if template has no elements, false otherwise
     */
    public boolean isEmpty() {
        return getElementCount() == 0;
    }
    
    /**
     * Clear all elements in the template
     */
    public void clearElements() {
        if (this.elements != null) {
            this.elements.clear();
        }
    }
    
    /**
     * Get string representation of the template
     * @return String description of the template
     */
    @Override
    public String toString() {
        return "PrintTemplate{" +
                "InitDrawingBoardParam=" + initDrawingBoardParam +
                ", elements=" + elements +
                '}';
    }
    
    /**
     * Check if two PrintTemplate objects are equal
     * @param o Object to compare
     * @return True if objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrintTemplate that = (PrintTemplate) o;
        return Objects.equals(initDrawingBoardParam, that.initDrawingBoardParam) &&
                Objects.equals(elements, that.elements);
    }
    
    /**
     * Get hash code of the object
     * @return Hash code of the object
     */
    @Override
    public int hashCode() {
        return Objects.hash(initDrawingBoardParam, elements);
    }
}
