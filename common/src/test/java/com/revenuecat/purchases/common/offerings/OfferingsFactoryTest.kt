package com.revenuecat.purchases.common.offerings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.ProductType
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.common.BillingAbstract
import com.revenuecat.purchases.common.GoogleOfferingParser
import com.revenuecat.purchases.common.OfferingParser
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.strings.OfferingStrings
import com.revenuecat.purchases.utils.ONE_OFFERINGS_INAPP_PRODUCT_RESPONSE
import com.revenuecat.purchases.utils.ONE_OFFERINGS_RESPONSE
import com.revenuecat.purchases.utils.STUB_OFFERING_IDENTIFIER
import com.revenuecat.purchases.utils.STUB_PRODUCT_IDENTIFIER
import com.revenuecat.purchases.utils.stubINAPPStoreProduct
import com.revenuecat.purchases.utils.stubStoreProduct
import com.revenuecat.purchases.utils.stubSubscriptionOption
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class OfferingsFactoryTest {

    private val oneOfferingWithNoProductsResponse = JSONObject("{'offerings': [" +
        "{'identifier': '$STUB_OFFERING_IDENTIFIER', " +
        "'description': 'This is the base offering', " +
        "'packages': []}]," +
        "'current_offering_id': '$STUB_OFFERING_IDENTIFIER'}")
    private val oneOfferingResponse = JSONObject(ONE_OFFERINGS_RESPONSE)
    private val oneOfferingInAppProductResponse = JSONObject(ONE_OFFERINGS_INAPP_PRODUCT_RESPONSE)

    private val productId = STUB_PRODUCT_IDENTIFIER

    private lateinit var billing: BillingAbstract
    private lateinit var offeringParser: OfferingParser

    private lateinit var offeringsFactory: OfferingsFactory

    @Before
    fun setUp() {
        billing = mockk()
        offeringParser = GoogleOfferingParser()

        offeringsFactory = OfferingsFactory(
            billing = billing,
            offeringParser = offeringParser
        )
    }

    @Test
    fun `configuration error if no products configured`() {
        var purchasesError: PurchasesError? = null
        offeringsFactory.createOfferings(
            oneOfferingWithNoProductsResponse,
            { purchasesError = it },
            { fail("Expected error") }
        )
        assertThat(purchasesError).isNotNull
        assertThat(purchasesError!!.code).isEqualTo(PurchasesErrorCode.ConfigurationError)
        assertThat(purchasesError!!.underlyingErrorMessage).contains(
            OfferingStrings.CONFIGURATION_ERROR_NO_PRODUCTS_FOR_OFFERINGS
        )
    }

    @Test
    fun `createOfferings returns error if json with wrong format`() {
        var purchasesError: PurchasesError? = null
        offeringsFactory.createOfferings(
            JSONObject("{}"),
            { purchasesError = it },
            { fail("Expected error") }
        )
        assertThat(purchasesError).isNotNull
        assertThat(purchasesError!!.code).isEqualTo(PurchasesErrorCode.UnexpectedBackendResponseError)
    }

    @Test
    fun `configuration error if products are not set up when fetching offerings`() {
        val productIds = listOf(productId)
        mockStoreProduct(productIds, listOf(), ProductType.SUBS)
        mockStoreProduct(productIds, listOf(), ProductType.INAPP)

        var purchasesError: PurchasesError? = null
        offeringsFactory.createOfferings(
            oneOfferingResponse,
            { purchasesError = it },
            { fail("Expected error") }
        )

        assertThat(purchasesError).isNotNull
        assertThat(purchasesError!!.code).isEqualTo(PurchasesErrorCode.ConfigurationError)
        assertThat(purchasesError!!.underlyingErrorMessage).contains(
            OfferingStrings.CONFIGURATION_ERROR_PRODUCTS_NOT_FOUND
        )
    }

    @Test
    fun `returns offerings when products found as subs`() {
        val productIds = listOf(productId)
        mockStoreProduct(productIds, productIds, ProductType.SUBS)

        var offerings: Offerings? = null
        offeringsFactory.createOfferings(
            oneOfferingResponse,
            { fail("Expected success. Got error: $it") },
            { offerings = it }
        )

        assertThat(offerings).isNotNull
        assertThat(offerings!!.all.size).isEqualTo(1)
        assertThat(offerings!![STUB_OFFERING_IDENTIFIER]!!.monthly!!.product).isNotNull
    }

    @Test
    fun `returns offerings when products found as inapp`() {
        val productIds = listOf(productId)
        mockStoreProduct(productIds, emptyList(), ProductType.SUBS)
        mockStoreProduct(productIds, productIds, ProductType.INAPP)

        var offerings: Offerings? = null
        offeringsFactory.createOfferings(
            oneOfferingInAppProductResponse,
            { fail("Expected success. Got error: $it") },
            { offerings = it }
        )

        assertThat(offerings).isNotNull
        assertThat(offerings!!.all.size).isEqualTo(1)
        assertThat(offerings!![STUB_OFFERING_IDENTIFIER]!!.monthly!!.product).isNotNull
    }

    // region helpers

    private fun mockStoreProduct(
        productIds: List<String> = listOf(productId),
        productIdsSuccessfullyFetched: List<String> = listOf(productId),
        type: ProductType = ProductType.SUBS
    ): List<StoreProduct> {
        val storeProducts = productIdsSuccessfullyFetched.map { productId ->
            if (type == ProductType.SUBS) stubStoreProduct(productId, stubSubscriptionOption("p1m", "P1M"))
            else stubINAPPStoreProduct(productId)
        }.map { it }

        every {
            billing.queryProductDetailsAsync(
                type,
                productIds.toSet(),
                captureLambda(),
                any()
            )
        } answers {
            lambda<(List<StoreProduct>) -> Unit>().captured.invoke(storeProducts)
        }
        return storeProducts
    }

    // endregion helpers
}