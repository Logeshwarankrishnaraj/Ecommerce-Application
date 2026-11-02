package com.commerce.catalog;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "product_attribute_values")
public class ProductAttributeValue {
    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "product_id")
        private Long productId;
        @Column(name = "attribute_code")
        private String attributeCode;

        public Key() {}

        public Key(Long productId, String attributeCode) {
            this.productId = productId;
            this.attributeCode = attributeCode;
        }

        @Override public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(productId, k.productId) && Objects.equals(attributeCode, k.attributeCode);
        }

        @Override public int hashCode() {
            return Objects.hash(productId, attributeCode);
        }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getAttributeCode() { return attributeCode; }
    public void setAttributeCode(String attributeCode) { this.attributeCode = attributeCode; }
}

    @EmbeddedId
    private Key id;
    private String valueCode;
    private String textValue;

    public Key getId() { return id; }
    public void setId(Key id) { this.id = id; }
    public String getValueCode() { return valueCode; }
    public void setValueCode(String valueCode) { this.valueCode = valueCode; }
    public String getTextValue() { return textValue; }
    public void setTextValue(String textValue) { this.textValue = textValue; }
}
