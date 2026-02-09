import enum

class UserRole(enum.Enum):
    USER = "USER"
    MANAGER = "MANAGER"
    ADMIN = "ADMIN"
    
class Entity(enum.Enum):
    PRODUCT = "PRODUCT"
    CATEGORY = "CATEGORY"
    USER = "USER"
    STOCK = "STOCK"
    MOVEMENT = "MOVEMENT"
    EVENT = "EVENT"
    STOCK_THRESHOLD = "STOCK_THRESHOLD"
    ALERT = "ALERT"
    
class ActionType(enum.Enum):
    CREATE = "CREATE"
    UPDATE = "UPDATE"
    DELETE = "DELETE"

class Source(enum.Enum):
    SCAN = "SCAN"
    MANUAL = "MANUAL"

class MovementType(enum.Enum):
    IN = "IN"
    OUT = "OUT"
    ADJUST = "ADJUST"

class EventType(enum.Enum):
    SENSOR_IN = "SENSOR_IN"
    SENSOR_OUT = "SENSOR_OUT"
    
class EventStatus(enum.Enum):
    PROCESSED = "PROCESSED"
    PENDING = "PENDING"
    ERROR = "ERROR"
    
class AlertStatus(enum.Enum):
    RESOLVED = "RESOLVED"
    ACK = "ACK"
    PENDING = "PENDING"

class AlertType(enum.Enum):
    LOW_STOCK = "LOW_STOCK"
    OUT_OF_STOCK = "OUT_OF_STOCK"
    LARGE_MOVEMENT = "LARGE_MOVEMENT"
    TRANSFER_COMPLETE = "TRANSFER_COMPLETE"
    IMPORT_ISSUES = "IMPORT_ISSUES"
