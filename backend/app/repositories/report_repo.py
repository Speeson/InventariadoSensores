from typing import Iterable, Tuple
from sqlalchemy import select, func, case
from sqlalchemy.orm import Session
from app.models.movement import Movement
from app.models.product import Product
from app.models.stock import Stock
from app.models.enums import MovementType
from app.repositories import location_repo


def list_top_consumed(
    db: Session,
    *,
    date_from=None,
    date_to=None,
    location: str | None = None,
    limit: int = 10,
    offset: int = 0,
) -> Tuple[Iterable[tuple], int]:
    filters = [Movement.movement_type == MovementType.OUT]
    if date_from:
        filters.append(Movement.created_at >= date_from)
    if date_to:
        filters.append(Movement.created_at <= date_to)
    if location:
        loc = location_repo.get_by_code(db, location)
        if not loc:
            return [], 0  # ubicación inexistente: sin resultados
        filters.append(Movement.location_id == loc.id)

    base = (
        select(
            Movement.product_id,
            Product.sku,
            Product.name,
            func.sum(Movement.quantity).label("total_out"),
        )
        .join(Product, Product.id == Movement.product_id)
        .where(*filters)
        .group_by(Movement.product_id, Product.sku, Product.name)
        .order_by(func.sum(Movement.quantity).desc())
    )

    total = db.scalar(select(func.count()).select_from(base.subquery())) or 0
    rows = db.execute(base.offset(offset).limit(limit)).all()
    return rows, total


def list_turnover(
    db: Session,
    *,
    date_from=None,
    date_to=None,
    location: str | None = None,
    limit: int = 10,
    offset: int = 0,
) -> Tuple[Iterable[tuple], int, str | None]:
    filters = []
    if date_from:
        filters.append(Movement.created_at >= date_from)
    if date_to:
        filters.append(Movement.created_at <= date_to)

    loc_id = None
    loc_code = None
    if location:
        loc = location_repo.get_by_code(db, location)
        if not loc:
            return [], 0, None  # ubicación inexistente
        loc_id = loc.id
        loc_code = loc.code
        filters.append(Movement.location_id == loc_id)

    mov_agg = (
        select(
            Movement.product_id,
            func.sum(
                case((Movement.movement_type == MovementType.OUT, Movement.quantity), else_=0)
            ).label("outs"),
            func.sum(
                case(
                    (Movement.movement_type == MovementType.IN, Movement.quantity),
                    (Movement.movement_type == MovementType.OUT, -Movement.quantity),
                    (Movement.movement_type == MovementType.ADJUST, Movement.quantity),
                    else_=0,
                )
            ).label("net"),
        )
        .where(*filters)
        .group_by(Movement.product_id)
        .cte("mov_agg")
    )

    stock_filters = []
    if loc_id is not None:
        stock_filters.append(Stock.location_id == loc_id)

    stock_agg = (
        select(
            Stock.product_id,
            func.sum(Stock.quantity).label("stock_final"),
        )
        .where(*stock_filters)
        .group_by(Stock.product_id)
        .subquery()
    )

    base = (
        select(
            Product.id.label("product_id"),
            Product.sku,
            Product.name,
            func.coalesce(stock_agg.c.stock_final, 0).label("stock_final"),
            func.coalesce(mov_agg.c.outs, 0).label("outs"),
            func.coalesce(mov_agg.c.net, 0).label("net"),
        )
        .join(mov_agg, mov_agg.c.product_id == Product.id, isouter=True)
        .join(stock_agg, stock_agg.c.product_id == Product.id, isouter=True)
    )

    # Excluir productos sin movimientos ni stock en la ubicación filtrada
    if loc_id is not None:
        base = base.where(
            (mov_agg.c.product_id.isnot(None)) | (stock_agg.c.product_id.isnot(None))
        )

    # Cálculos derivados
    stock_final = func.coalesce(stock_agg.c.stock_final, 0)
    outs = func.coalesce(mov_agg.c.outs, 0)
    net = func.coalesce(mov_agg.c.net, 0)
    stock_initial = stock_final - net
    stock_average = (stock_initial + stock_final) / 2
    turnover = case(
        (stock_average > 0, outs / stock_average),
        else_=None,
    )

    base = base.add_columns(
        stock_initial.label("stock_initial"),
        stock_average.label("stock_average"),
        turnover.label("turnover"),
    )

    total = db.scalar(select(func.count()).select_from(base.subquery())) or 0
    rows = db.execute(
        base.order_by(turnover.desc().nulls_last()).offset(offset).limit(limit)
    ).all()

    return rows, total, loc_code
