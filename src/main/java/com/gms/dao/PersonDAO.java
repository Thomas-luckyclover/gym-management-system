package com.gms.dao;

import com.gms.entity.Person;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Truy vấn bảng Persons.
 */
public class PersonDAO {

    public Person findById(int personId) throws SQLException {
        String sql
                = "SELECT PersonId, FullName, DateOfBirth, Phone, Email, "
                + "Avatar, IsReleased, CreatedAt "
                + "FROM Persons "
                + "WHERE PersonId = ?";

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setInt(1, personId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToPerson(resultSet);
                }
            }
        }

        return null;
    }

    private Person mapResultSetToPerson(ResultSet resultSet)
            throws SQLException {

        return new Person(
                resultSet.getInt("PersonId"),
                resultSet.getString("FullName"),
                resultSet.getObject(
                        "DateOfBirth",
                        java.time.LocalDate.class
                ),
                resultSet.getString("Phone"),
                resultSet.getString("Email"),
                resultSet.getString("Avatar"),
                resultSet.getBoolean("IsReleased"),
                resultSet.getObject(
                        "CreatedAt",
                        java.time.LocalDateTime.class
                )
        );
    }
}