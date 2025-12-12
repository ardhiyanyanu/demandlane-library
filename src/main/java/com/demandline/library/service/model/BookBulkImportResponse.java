package com.demandline.library.service.model;

public record BookBulkImportResponse(
        Integer importedCount,
        Integer updatedCount,
        Integer failedCount
) {}
