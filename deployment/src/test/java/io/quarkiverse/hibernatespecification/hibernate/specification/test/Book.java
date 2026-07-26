package io.quarkiverse.hibernatespecification.hibernate.specification.test;

import jakarta.persistence.*;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String title;
    public String genre;
    public Double price;

    @ManyToOne(fetch = FetchType.LAZY)
    public Author author;

    public Book() {
    }

    public Book(Long id, String title, String genre, Double price, Author author) {
        this.id = id;
        this.title = title;
        this.genre = genre;
        this.price = price;
        this.author = author;
    }

    public static Book of(String title, String genre, double price) {
        Book b = new Book();
        b.title = title;
        b.genre = genre;
        b.price = price;
        return b;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }
}
