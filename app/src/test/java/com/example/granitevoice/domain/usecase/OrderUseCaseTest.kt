package com.example.granitevoice.domain.usecase

import com.example.granitevoice.data.repository.OrderRepositoryImpl
import com.example.granitevoice.data.remote.OrderProgressGraniteSource
import com.example.granitevoice.domain.model.Order
import com.example.granitevoice.domain.model.TradingPartner
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for OrderUseCase
 */
class OrderUseCaseTest {

    @Mock
    private lateinit var orderRepository: OrderRepositoryImpl

    @Mock
    private lateinit var orderProgressGraniteSource: OrderProgressGraniteSource

    private lateinit var getOrdersUseCase: GetOrdersUseCase

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        getOrdersUseCase = GetOrdersUseCase(orderRepository, orderProgressGraniteSource)
    }

    @Test
    fun `checkOrderStatus returns success when order exists`() = runTest {
        // Given
        val orderNumber = "ORD-12345"
        val mockOrder = Order(
            id = 1,
            number = orderNumber,
            tradingPartnerCode = "CUST001",
            tradingPartnerDescription = "Test Customer",
            createDate = "15/01/2025",
            site = "MAIN",
            status = "ENTERED",
            auditDate = "15/01/2025",
            auditUser = "TESTUSER",
            type = "ORDER",
            description = "Test order",
            priority = 1,
            erpLocation = "LOC001",
            lastSyncAt = System.currentTimeMillis()
        )

        whenever(orderRepository.getOrderByNumber(orderNumber))
            .thenReturn(Result.success(mockOrder))

        // When
        val result = getOrdersUseCase.checkOrderStatus(orderNumber)

        // Then
        assertThat(result.isSuccess).isTrue()
        result.onSuccess { statusInfo ->
            assertThat(statusInfo.orderNumber).isEqualTo(orderNumber)
            assertThat(statusInfo.status).isEqualTo("ENTERED")
            assertThat(statusInfo.statusDisplay).isEqualTo("Entered")
            assertThat(statusInfo.isActive).isTrue()
        }
    }

    @Test
    fun `checkOrderStatus returns failure when order not found`() = runTest {
        // Given
        val orderNumber = "NONEXISTENT"

        whenever(orderRepository.getOrderByNumber(orderNumber))
            .thenReturn(Result.success(null))

        // When
        val result = getOrdersUseCase.checkOrderStatus(orderNumber)

        // Then
        assertThat(result.isFailure).isTrue()
        result.onFailure { exception ->
            assertThat(exception).isInstanceOf(NoSuchElementException::class.java)
            assertThat(exception.message).contains("Order not found: $orderNumber")
        }
    }

    @Test
    fun `getDistinctTradingPartners returns partner list`() = runTest {
        // Given
        val mockPartners = listOf(
            TradingPartner("CUST001", "Customer One"),
            TradingPartner("CUST002", "Customer Two")
        )

        whenever(orderRepository.getDistinctTradingPartners())
            .thenReturn(Result.success(mockPartners))

        // When
        val result = getOrdersUseCase.getDistinctTradingPartners()

        // Then
        assertThat(result.isSuccess).isTrue()
        result.onSuccess { partners ->
            assertThat(partners).hasSize(2)
            assertThat(partners[0].code).isEqualTo("CUST001")
            assertThat(partners[0].description).isEqualTo("Customer One")
        }
    }

    @Test
    fun `searchOrdersByNumber handles empty search term`() = runTest {
        // When
        val result = getOrdersUseCase.searchOrdersByNumber("")

        // Then
        assertThat(result.isSuccess).isTrue()
        result.onSuccess { orders ->
            assertThat(orders).isEmpty()
        }
    }

    @Test
    fun `order entity status display works correctly`() {
        // Test various status mappings
        val orderEntered = Order(
            id = 1,
            number = "TEST1",
            tradingPartnerCode = "T1",
            tradingPartnerDescription = "Test 1",
            createDate = "15/01/2025",
            site = "MAIN",
            status = "ENTERED",
            auditDate = "15/01/2025",
            auditUser = "USER",
            type = "ORDER"
        )

        val orderComplete = orderEntered.copy(status = "COMPLETE")
        val orderCancelled = orderEntered.copy(status = "CANCELLED")

        assertThat(orderEntered.getStatusDisplay()).isEqualTo("Entered")
        assertThat(orderComplete.getStatusDisplay()).isEqualTo("Complete")
        assertThat(orderCancelled.getStatusDisplay()).isEqualTo("Cancelled")

        assertThat(orderEntered.isActive()).isTrue()
        assertThat(orderComplete.isActive()).isFalse()
        assertThat(orderCancelled.isActive()).isFalse()
    }
}