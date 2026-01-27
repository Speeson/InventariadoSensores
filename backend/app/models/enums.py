import enum

class UserRole(enum.Enum):
    USER = "USER"
    MANAGER = "MANAGER"
    ADMIN = "ADMIN"
    
class ActionType(enum.Enum):
    CREATE = "CREATE"
    UPDATE = "UPDATE"
    DELETE = "DELETE"

class EventType(enum.Enum):
    SENSOR_IN = "SENSOR_IN"
    SENSOR_OUT = "SENSOR_OUT"

class MovementType(enum.Enum):
    IN = "IN"
    OUT = "OUT"
    ADJUST = "ADJUST"

class MovementSource(enum.Enum):
    SCAN = "SCAN"
    MANUAL = "MANUAL"