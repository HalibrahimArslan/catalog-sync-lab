package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.domain.Sku;

public record CountStockCommand(Sku sku, int quantity) {
}
