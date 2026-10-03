package com.kora.ecommerce.catalog.batch;

final class SupplierImportRowValidationException extends RuntimeException {

    private final int rowNumber;

    SupplierImportRowValidationException(int rowNumber, String reason) {
        super(reason);
        this.rowNumber = rowNumber;
    }

    int getRowNumber() {
        return rowNumber;
    }
}
