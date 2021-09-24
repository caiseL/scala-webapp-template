package net.wiringbits.repositories.daos

import anorm.SqlStringInterpolation
import net.wiringbits.repositories.models.{Cell, ColumnMetadata, DatabaseTable, RowMetadata, TableMetadata}

import java.sql.{Connection, ResultSet}

object DatabaseTablesDAO {

  def all()(implicit conn: Connection): List[DatabaseTable] = {
    // TODO: Not generic enough
    SQL"""
      SELECT table_name
      FROM information_schema.tables
      WHERE table_schema='public'
      AND table_type='BASE TABLE'
      ORDER BY table_name
      """.as(tableParser.*)
  }

  def getTableMetadata(tableName: String)(implicit conn: Connection): TableMetadata = {
    val tableData: Array[RowMetadata] = Array()
    val columnsMetadata: Array[ColumnMetadata] = Array()

    val statement = conn.createStatement(ResultSet.CONCUR_READ_ONLY, ResultSet.CONCUR_READ_ONLY)
    val resultSet = statement.executeQuery("SELECT * FROM " + tableName)
    val metadata = resultSet.getMetaData

    val numberOfColumns = metadata.getColumnCount

    // It goes into the rows one by one
    while (resultSet.next) {
      val rowData: Array[Cell] = Array()
      for (columnNumber <- 1 to numberOfColumns) {
        val columnName = metadata.getColumnName(columnNumber)
        val columnType = metadata.getColumnTypeName(columnNumber)

        val columnMetadata = ColumnMetadata(columnName, columnType)
        columnsMetadata :+ columnMetadata

        val cell = Cell(resultSet.getString(columnName))
        rowData :+ cell

        println(resultSet.getObject(columnName))
        println(cell)
      }
      tableData :+ rowData
    }

    val tableMetadata = TableMetadata(tableName, columnsMetadata.toList, tableData.toList)
    println(tableData.mkString("Array(", ", ", ")"))
    println(tableMetadata)

    tableMetadata
  }
}
