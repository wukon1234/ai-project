package com.example.rag.dto;

import lombok.Data;

@Data
public class UploadResponse {
    private boolean success;
    private String message;
    private String fileName;
    private int pages;
}
