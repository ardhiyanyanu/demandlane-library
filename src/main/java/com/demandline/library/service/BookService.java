package com.demandline.library.service;

import com.demandline.library.repository.BookRepository;
import com.demandline.library.service.model.Book;
import com.demandline.library.service.model.BookBulkImportResponse;
import com.demandline.library.service.model.filter.BookFilter;
import com.demandline.library.service.model.input.BookInput;
import com.demandline.library.service.model.input.BookUpdateInput;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class BookService {
    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public Book createBook(BookInput bookInput) {
        return null;
    }

    public BookBulkImportResponse createMultipleBook(MultipartFile file) {
        return null;
    }

    public Book updateBook(BookUpdateInput updatedBook) {

        // need to check if book lended out before reducing totalCopies
        return null;
    }

    public void deleteBook(String bookId) {

    }

    public Book getBookById(String bookId) {
        return null;
    }

    public List<Book> getAllBooks(BookFilter bookNameFilter, int limit, int offset) {
        return List.of();
    }
}
