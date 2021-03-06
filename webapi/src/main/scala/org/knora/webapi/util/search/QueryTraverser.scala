/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util.search

/**
  * A trait for classes that transform statements and filters in WHERE clauses. Such a class will probably need
  * to refer to a [[TypeInspectionResult]].
  */
trait WhereTransformer {
    /**
      * Transforms a [[StatementPattern]] in a WHERE clause into zero or more query patterns.
      *
      * @param statementPattern the statement to be transformed.
      * @return the result of the transformation.
      */
    def transformStatementInWhere(statementPattern: StatementPattern): Seq[QueryPattern]

    /**
      * Transforms a [[FilterPattern]] in a WHERE clause into zero or more statement patterns.
      *
      * @param filterPattern the filter to be transformed.
      * @return the result of the transformation.
      */
    def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern]
}

/**
  * A trait for classes that transform SELECT queries into other SELECT queries.
  */
trait SelectToSelectTransformer extends WhereTransformer {

    /**
      * Transforms a [[StatementPattern]] in a SELECT's WHERE clause into zero or more statement patterns.
      *
      * @param statementPattern the statement to be transformed.
      * @return the result of the transformation.
      */
    def transformStatementInSelect(statementPattern: StatementPattern): Seq[StatementPattern]
}

/**
  * A trait for classes that transform CONSTRUCT queries into other CONSTRUCT queries.
  */
trait ConstructToConstructTransformer extends WhereTransformer {

    /**
      * Transforms a [[StatementPattern]] in a CONSTRUCT clause into zero or more statement patterns.
      *
      * @param statementPattern the statement to be transformed.
      * @return the result of the transformation.
      */
    def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern]
}

/**
  * Returned by `ConstructToSelectTransformer.getOrderBy` to represent a transformed ORDER BY as well
  * as any additional statement patterns that should be added to the WHERE clause to support the ORDER BY.
  *
  * @param statementPatterns any additional WHERE clause statements required by the ORDER BY.
  * @param orderBy           the ORDER BY criteria.
  */
case class TransformedOrderBy(statementPatterns: Seq[StatementPattern] = Vector.empty[StatementPattern], orderBy: Seq[OrderCriterion] = Vector.empty[OrderCriterion])

/**
  * A trait for classes that transform SELECT queries into CONSTRUCT queries.
  */
trait ConstructToSelectTransformer extends WhereTransformer {
    /**
      * Collects information from a statement pattern in the CONSTRUCT clause of the input query, e.g. variables
      * that need to be returned by the SELECT.
      *
      * @param statementPattern the statement to be handled.
      */
    def handleStatementInConstruct(statementPattern: StatementPattern): Unit

    /**
      * Returns the variables that should be included in the results of the SELECT query. This method will be called
      * by [[QueryTraverser]] after the whole input query has been traversed.
      *
      * @return the variables that should be returned by the SELECT.
      */
    def getSelectVariables: Seq[SelectQueryColumn]

    /**
      * Returns the variables that the query result rows are grouped by (aggregating rows into one).
      * Variables returned by the SELECT query must either be present in the GROUP BY statement
      * or be transformed by an aggregation function in SPARQL.
      * This method will be called by [[QueryTraverser]] after the whole input query has been traversed.
      *
      * @param orderByCriteria the criteria used to sort the query results. They have to be included in the GROUP BY statement, otherwise they are unbound.
      * @return a list of variables that the result rows are grouped by.
      */
    def getGroupBy(orderByCriteria: TransformedOrderBy): Seq[QueryVariable]

    /**
      * Returns the criteria, if any, that should be used in the ORDER BY clause of the SELECT query. This method will be called
      * by [[QueryTraverser]] after the whole input query has been traversed.
      *
      * @param inputOrderBy the ORDER BY criteria in the input query.
      * @return the ORDER BY criteria, if any.
      */
    def getOrderBy(inputOrderBy: Seq[OrderCriterion]): TransformedOrderBy

    /**
      * Returns the limit representing the maximum amount of result rows returned by the SELECT query.
      *
      * @return the LIMIT, if any.
      */
    def getLimit: Int

    /**
      * Returns the OFFSET to be used in the SELECT query.
      * Provided the OFFSET submitted in the input query, calculates the actual offset in result rows depending on LIMIT.
      *
      * @param inputQueryOffset the OFFSET provided in the input query.
      * @return the OFFSET.
      */
    def getOffset(inputQueryOffset: Long, limit: Int): Long
}


/**
  * Assists in the transformation of CONSTRUCT queries by traversing the query, delegating work to a [[ConstructToConstructTransformer]]
  * or [[ConstructToSelectTransformer]].
  */
object QueryTraverser {

    private def transformWherePatterns(patterns: Seq[QueryPattern], whereTransformer: WhereTransformer): Seq[QueryPattern] = {
        patterns.flatMap {
            case statementPattern: StatementPattern =>
                whereTransformer.transformStatementInWhere(
                    statementPattern = statementPattern
                )

            case filterPattern: FilterPattern =>
                whereTransformer.transformFilter(
                    filterPattern = filterPattern
                )

            case filterNotExistsPattern: FilterNotExistsPattern =>
                val transformedPatterns: Seq[QueryPattern] = transformWherePatterns(
                    patterns = filterNotExistsPattern.patterns,
                    whereTransformer = whereTransformer
                )

                Seq(FilterNotExistsPattern(patterns = transformedPatterns))


            case minusPattern: MinusPattern =>
                val transformedPatterns: Seq[QueryPattern] = transformWherePatterns(
                    patterns = minusPattern.patterns,
                    whereTransformer = whereTransformer
                )

                Seq(MinusPattern(patterns = transformedPatterns))

            case optionalPattern: OptionalPattern =>
                val transformedPatterns = transformWherePatterns(
                    patterns = optionalPattern.patterns,
                    whereTransformer = whereTransformer
                )

                Seq(OptionalPattern(patterns = transformedPatterns))

            case unionPattern: UnionPattern =>
                val transformedBlocks = unionPattern.blocks.map {
                    blockPatterns =>
                        transformWherePatterns(patterns = blockPatterns,
                            whereTransformer = whereTransformer
                        )
                }

                Seq(UnionPattern(blocks = transformedBlocks))

            case valuesPattern: ValuesPattern => Seq(valuesPattern)

        }
    }

    /**
      * Traverses a SELECT query, delegating transformation tasks to a [[ConstructToSelectTransformer]], and returns the transformed query.
      *
      * @param inputQuery  the query to be transformed.
      * @param transformer the [[ConstructToSelectTransformer]] to be used.
      * @return the transformed query.
      */
    def transformConstructToSelect(inputQuery: ConstructQuery, transformer: ConstructToSelectTransformer): SelectQuery = {

        for (statement <- inputQuery.constructClause.statements) {
            transformer.handleStatementInConstruct(statement)
        }

        val transformedWherePatterns = transformWherePatterns(
            patterns = inputQuery.whereClause.patterns,
            whereTransformer = transformer
        )

        val transformedOrderBy = transformer.getOrderBy(inputQuery.orderBy)

        val groupBy: Seq[QueryVariable] = transformer.getGroupBy(transformedOrderBy)

        val limit: Int = transformer.getLimit

        val offset = transformer.getOffset(inputQuery.offset, limit)

        SelectQuery(
            variables = transformer.getSelectVariables,
            useDistinct = true,
            whereClause = WhereClause(patterns = transformedWherePatterns ++ transformedOrderBy.statementPatterns),
            groupBy = groupBy,
            orderBy = transformedOrderBy.orderBy,
            limit = Some(limit),
            offset = offset
        )
    }

    def transformSelectToSelect(inputQuery: SelectQuery, transformer: SelectToSelectTransformer): SelectQuery = {
        inputQuery.copy(
            whereClause = WhereClause(
                patterns = transformWherePatterns(
                    patterns = inputQuery.whereClause.patterns,
                    whereTransformer = transformer
                )
            )
        )
    }

    /**
      * Traverses a CONSTRUCT query, delegating transformation tasks to a [[ConstructToConstructTransformer]], and returns the transformed query.
      *
      * @param inputQuery  the query to be transformed.
      * @param transformer the [[ConstructToConstructTransformer]] to be used.
      * @return the transformed query.
      */
    def transformConstructToConstruct(inputQuery: ConstructQuery, transformer: ConstructToConstructTransformer): ConstructQuery = {

        val transformedWherePatterns = transformWherePatterns(
            patterns = inputQuery.whereClause.patterns,
            whereTransformer = transformer
        )

        val transformedConstructStatements: Seq[StatementPattern] = inputQuery.constructClause.statements.flatMap {
            statementPattern => transformer.transformStatementInConstruct(statementPattern)
        }

        ConstructQuery(
            constructClause = ConstructClause(statements = transformedConstructStatements),
            whereClause = WhereClause(patterns = transformedWherePatterns)
        )
    }
}
