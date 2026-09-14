package io.radius.user.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "tag")
public class Tag {

    /** skill · teach · need · hobby · item */
    public enum Kind { skill, teach, need, hobby, item }

    @Id
    private UUID id;

    private String slug;
    private String label;
    private String kind;

    protected Tag() {}

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getLabel() { return label; }
    public String getKind() { return kind; }
}
