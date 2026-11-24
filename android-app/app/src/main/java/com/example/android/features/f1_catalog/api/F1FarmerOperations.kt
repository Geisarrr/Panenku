package id.panenku.app.features.f1_catalog.api

import id.panenku.app.data.*

interface F1FarmerOperations {
    suspend fun saveProduct(product: Product)
}
