package com.gms.dao;

import com.gms.constant.Role;
import com.gms.entity.Employee;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Truy vấn bảng Employees.
 */
public class EmployeeDAO {

    public Employee findByPersonId(int personId)
            throws SQLException {

        String sql
                = "SELECT EmployeeId, PersonId, EmployeeCode, Position, "
                + "HireDate, CommissionRate, Specialty, "
                + "YearsOfExperience, Status, DeletedAt, "
                + "ResignationReason "
                + "FROM Employees "
                + "WHERE PersonId = ?";

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setInt(1, personId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToEmployee(resultSet);
                }
            }
        }

        return null;
    }

    private Employee mapResultSetToEmployee(ResultSet resultSet)
            throws SQLException {

        Integer commissionRate
                = getNullableInteger(resultSet, "CommissionRate");

        Integer yearsOfExperience
                = getNullableInteger(
                        resultSet,
                        "YearsOfExperience"
                );

        return new Employee(
                resultSet.getInt("EmployeeId"),
                resultSet.getInt("PersonId"),
                resultSet.getString("EmployeeCode"),
                Role.fromDbValue(
                        resultSet.getString("Position")
                ),
                resultSet.getObject(
                        "HireDate",
                        java.time.LocalDate.class
                ),
                commissionRate,
                resultSet.getString("Specialty"),
                yearsOfExperience,
                Employee.Status.fromDbValue(
                        resultSet.getString("Status")
                ),
                resultSet.getObject(
                        "DeletedAt",
                        java.time.LocalDateTime.class
                ),
                resultSet.getString("ResignationReason")
        );
    }

    private Integer getNullableInteger(
            ResultSet resultSet,
            String columnName
    ) throws SQLException {

        int value = resultSet.getInt(columnName);

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }
}