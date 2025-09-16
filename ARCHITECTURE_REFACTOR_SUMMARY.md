# Architecture Refactor: Strategy and Factory Pattern Implementation

## Summary

This refactor implements the suggested Strategy and Factory pattern architecture for the voice
command processing system, replacing the monolithic approach with a more scalable and maintainable
design. Additionally, it ensures strict adherence to the Single Responsibility Principle by making
the VoiceProcessingRepository truly generic and domain-agnostic.

## Key Changes Made

### 1. Expanded CommandIntent (VoiceCommand.kt)
- Added `CreateOrderItem(itemName: String, quantity: Int)`
- Added `CheckOrderStatus(orderId: String)`
- Maintained existing `StockLookup` and `Unknown` intents

### 2. Created Domain Models
- **Order.kt**: Complete order domain model with status tracking
- **OrderRepository.kt**: Interface for order-related operations
- **OrderRepositoryImpl.kt**: Basic in-memory implementation

### 3. Specific Use Cases (Single Responsibility Principle)

- **ProcessStockLookupUseCase.kt**: Extracted stock lookup logic with domain-specific query
  extraction
- **CreateOrderItemUseCase.kt**: Handles order creation workflow
- **CheckOrderStatusUseCase.kt**: Manages order status queries

### 4. Router/Orchestrator Pattern

- **RouteVoiceCommandUseCase.kt**: Strategy pattern implementation that delegates to specific use
  cases
- **ProcessVoiceCommandUseCase.kt**: Maintained as legacy wrapper for backward compatibility

### 5. Enhanced Intent Recognition
- **RecognizeIntentUseCase.kt**: Updated to recognize new command patterns:
  - "create order for ABC quantity 5"
  - "order 10 of item XYZ"
  - "check order ORD-12345678"

### 6. Generic and Focused Repository Responsibilities

- **VoiceProcessingRepository**: Now truly generic - only provides reusable language processing
  tools:
  - `cleanTranscription()`: Generic text cleaning (removes fillers, corrects speech errors)
  - `findSimilarItems()`: Domain-agnostic fuzzy matching
- **Domain-specific logic moved to Use Cases**: Each use case now contains its own logic for
  interpreting cleaned text:
  - Stock lookup: Extracts item queries from cleaned transcriptions
  - Order creation: Cleans item names for better matching
  - Order status: Cleans order IDs for accurate lookup

### 7. Dependency Injection Updates
- **AppModule.kt**: Added OrderRepository binding
- All new use cases automatically injectable via Hilt

## Architecture Benefits Achieved

### Strict Single Responsibility Principle

- **VoiceProcessingRepository**: Now 100% generic and reusable for ANY voice command type
- **Use Cases**: Each contains ALL logic related to its specific domain (stock, orders, etc.)
- **Clear Separation**: Generic language processing vs. domain-specific interpretation

### Increased Cohesion

- All stock lookup logic (including query extraction) lives in `ProcessStockLookupUseCase`
- All order creation logic lives in `CreateOrderItemUseCase`
- All order status logic lives in `CheckOrderStatusUseCase`
- Related code is grouped together

### Open/Closed Principle

- New voice commands can be added by creating new CommandIntent types and UseCase classes
- No modification of existing use cases or the voice processing repository required
- **VoiceProcessingRepository never needs changes** when adding new command types

### Strategy Pattern Implementation
- `RouteVoiceCommandUseCase` acts as the strategy context
- Specific use cases act as concrete strategies
- Intent determines which strategy is executed

### Future-Proof Design

- Adding "Get item details for X" command requires:
  1. Create `GetItemDetailsUseCase`
  2. Add logic to interpret cleaned transcription for item details
  3. **No changes to VoiceProcessingRepository needed**
- The repository's generic tools work for any voice command domain

### Improved Testability
- Each use case can be tested in complete isolation
- Dependencies are clearly defined and injectable
- Business logic separated from infrastructure concerns
- Generic repository functions can be tested independently

### Enhanced Readability
- Voice app capabilities clearly defined by use case classes
- Processing flow is explicit and traceable
- Logging and error handling consistent across use cases
- Clear distinction between generic text processing and domain logic

## Processing Flow Example

### Stock Lookup Command: "Find stock for ABC123"

```
1. RouteVoiceCommandUseCase
   ↓
2. RecognizeIntentUseCase → CommandIntent.StockLookup
   ↓  
3. ProcessStockLookupUseCase:
   a. VoiceProcessingRepository.cleanTranscription() → "find stock ABC123"
   b. extractItemQueryFromCleanedText() → "ABC123" (domain-specific)
   c. SearchItemsUseCase → Find matching items
   d. StockRepository → Get stock levels
```

### Order Creation Command: "Create order for Widget123 quantity 5"

```
1. RouteVoiceCommandUseCase
   ↓
2. RecognizeIntentUseCase → CommandIntent.CreateOrderItem("Widget123", 5)
   ↓
3. CreateOrderItemUseCase:
   a. VoiceProcessingRepository.cleanTranscription("Widget123") → "widget123"  
   b. MasterItemRepository → Find item
   c. OrderRepository → Create order
```

## Voice Command Examples

### Stock Lookup (Existing)
```
"Find stock for ABC123"
"Do we have any widgets?"
"Check inventory for part XYZ"
```

### Order Creation (New)
```
"Create order for ABC123 quantity 5"
"Order 10 of item widgets"  
"Add 3 pieces of XYZ to order"
```

### Order Status (New)
```
"Check order ORD-12345678"
"Status of order ABC-99999"
"Track order XYZ-88888"
```

## Repository Responsibilities

### VoiceProcessingRepository (Generic)

- Remove filler words ("uh", "um", "please")
- Correct common speech errors ("fine" → "find")
- Normalize text (lowercase, trim spaces)
- Fuzzy match text against item lists
- **Domain-agnostic operations only**

### Use Cases (Domain-Specific)

- **ProcessStockLookupUseCase**: Extract item queries from "find stock for X"
- **CreateOrderItemUseCase**: Process item names for order creation
- **CheckOrderStatusUseCase**: Clean order IDs for lookup
- **Future use cases**: Will contain their own interpretation logic

## Backward Compatibility

- `ProcessVoiceCommandUseCase` maintained as wrapper
- Existing API contracts preserved
- Gradual migration path available

## Future Extensibility

Adding new voice commands now follows a simple pattern and **never requires repository changes**:

1. Add new `CommandIntent` type
2. Create specific UseCase class with its own interpretation logic
3. Use `VoiceProcessingRepository.cleanTranscription()` for generic cleaning
4. Add domain-specific text interpretation in the use case
5. Update `RecognizeIntentUseCase` patterns
6. Add routing logic to `RouteVoiceCommandUseCase`

**Example**: Adding "Get item details for X":

```kotlin
class GetItemDetailsUseCase {
    suspend operator fun invoke(transcription: String): VoiceCommandResult {
        // 1. Use generic cleaning
        val cleaned = voiceProcessingRepository.cleanTranscription(transcription)
        
        // 2. Domain-specific extraction (lives here, not in repository)
        val itemQuery = extractItemFromDetailsCommand(cleaned) // "details for X" → "X"
        
        // 3. Business logic...
    }
}
```

The architecture is now both more scalable and strictly adheres to clean architecture principles
with proper separation of concerns!

## Key Improvement Summary

**Before**: VoiceProcessingRepository contained stock-specific logic mixed with generic cleaning
**After**: VoiceProcessingRepository is purely generic, domain logic lives in appropriate use cases
**Result**: True Single Responsibility Principle adherence and maximum reusability