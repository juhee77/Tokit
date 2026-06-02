# 💧 Liquibase 마이그레이션 가이드 (vs Flyway)

본 프로젝트는 현재 직관적인 SQL 스크립트 기반의 **Flyway**를 채택하고 있으나, 멀티 데이터베이스 지원이나 롤백(Rollback) 기능이 강력하게 요구되는 엔터프라이즈 환경에서는 **Liquibase**를 대안으로 사용할 수 있습니다. 

이 문서는 추후 프로젝트를 Liquibase로 전환할 경우 필요한 설정과 작성 방식의 차이를 안내합니다.

---

## 1. Liquibase란?

Liquibase는 순수 SQL뿐만 아니라 **XML, YAML, JSON** 포맷으로 데이터베이스 스키마의 변경 사항(ChangeLog)을 추적하고 관리하는 오픈소스 라이브러리입니다.
가장 큰 장점은 **데이터베이스 종속성(Dialect)을 추상화**한다는 것입니다. 예를 들어 YAML로 "테이블 생성"을 정의해 두면, PostgreSQL, MySQL, Oracle 등 연결된 DB의 종류에 맞춰 알맞은 쿼리로 자동 번역하여 실행해 줍니다.

---

## 2. Liquibase 세팅 방법

현재 설정된 Flyway를 Liquibase로 교체하려면 아래 3단계를 수행합니다.

### Step 1: `build.gradle` 의존성 변경

```gradle
// 기존 Flyway 의존성 제거
// implementation 'org.flywaydb:flyway-core'
// implementation 'org.flywaydb:flyway-database-postgresql'

// Liquibase 의존성 추가
implementation 'org.liquibase:liquibase-core'
```

### Step 2: `application.yml` 설정 변경

```yaml
spring:
  # Flyway 비활성화
  # flyway:
  #   enabled: false
  
  # Liquibase 활성화
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.yaml
```

### Step 3: ChangeLog 파일 작성

Liquibase는 주로 `src/main/resources/db/changelog` 디렉토리 하위에 변경 이력을 기록합니다. 순수 SQL 방식의 Flyway(`V1__init.sql`)와 달리, YAML 형식으로 작성하면 다음과 같습니다.

**`db.changelog-master.yaml` 예시 (Users 및 Wallets 테이블)**

```yaml
databaseChangeLog:
  - changeSet:
      id: 1
      author: tokit-architect
      changes:
        - createTable:
            tableName: users
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: name
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
              - column:
                  name: kyc_status
                  type: BOOLEAN
                  defaultValueBoolean: false
                  constraints:
                    nullable: false

  - changeSet:
      id: 2
      author: tokit-architect
      changes:
        - createTable:
            tableName: wallets
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
              - column:
                  name: balance
                  type: NUMERIC(20, 4)
                  defaultValueNumeric: 0
              - column:
                  name: user_id
                  type: BIGINT
        - addForeignKeyConstraint:
            baseTableName: wallets
            baseColumnNames: user_id
            referencedTableName: users
            referencedColumnNames: id
            constraintName: fk_wallets_user
```

---

## 3. Flyway vs Liquibase 요약 비교

| 구분 | Flyway (현재 적용) | Liquibase |
| :--- | :--- | :--- |
| **스크립트 포맷** | 순수 SQL (`.sql`) | XML, YAML, JSON, SQL |
| **장점** | - DB 고유 기능(특정 인덱스, 함수 등) 활용 용이<br>- 직관적이고 러닝 커브가 매우 낮음 | - DBMS 종류에 구애받지 않음 (멀티 DB 지원)<br>- 강력한 **Auto Rollback** 기능 지원 |
| **단점** | - DB 종류를 바꾸면 SQL 스크립트를 재작성해야 함 | - 문법이 장황(Verbose)함<br>- 복잡한 DB 종속적 기능은 결국 Raw SQL을 써야 함 |
| **사용 추천** | 단일 DB(PostgreSQL 등) 체제로 갈 확고한 프로덕트 | 납품형 솔루션 등 다양한 DB 환경을 지원해야 할 때 |

프로젝트의 성격에 맞게, 단일 DB 인프라에 집중하여 빠르고 직관적으로 관리할지(Flyway), 다양한 DB 지원과 롤백의 안전성을 챙길지(Liquibase) 선택할 수 있습니다. 
현재 우리의 TOKIT 프로젝트는 고성능 PostgreSQL 단일 환경에 최적화되어 있으므로 **Flyway**가 가장 빠르고 명확한 선택지입니다.
