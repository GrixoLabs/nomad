from datetime import datetime, timezone

from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.database.models.user import User


class UserRepository:
    def get_by_email_or_phone(
        self,
        db: Session,
        email: str | None,
        phone: str | None,
    ) -> User | None:
        clauses = []
        if email:
            clauses.append(User.email == email)
        if phone:
            clauses.append(User.phone == phone)
        if not clauses:
            return None
        return db.scalar(select(User).where(or_(*clauses)))

    def create(self, db: Session, user: User) -> User:
        db.add(user)
        db.commit()
        db.refresh(user)
        return user

    def update(self, db: Session, user: User) -> User:
        user.updated_utc = datetime.now(timezone.utc)
        db.commit()
        db.refresh(user)
        return user
