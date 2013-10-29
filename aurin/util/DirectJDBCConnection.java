package org.fao.geonet.aurin.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.Format;

/**
 * @author anasr
 *@deprecated
 */
public class DirectJDBCConnection {

	public static Connection getNewConnection(String ip,String port, String databaseName, String userName, String password) {

		System.out.println("-------- PostgreSQL "
				+ "JDBC Connection Testing ------------");

		try {

			Class.forName("org.postgresql.Driver");

		} catch (ClassNotFoundException e) {

			System.out.println("Where is your PostgreSQL JDBC Driver? "
					+ "Include in your library path!");
			e.printStackTrace();
			return null;

		}

		System.out.println("PostgreSQL JDBC Driver Registered!");

		Connection connection = null;

		try {

			String connectionStr = 	"jdbc:postgresql://%s:%s/%s".format(ip, port,databaseName);
			connection = DriverManager.getConnection(
					connectionStr, userName,
					password);
			return connection;
//			connection = DriverManager.getConnection(
//					"jdbc:postgresql://127.0.0.1:5432/geocoding", "postgres",
//					"Qwert123");

		} catch (SQLException e) {

			System.out.println("Connection Failed! Check output console");
			e.printStackTrace();
			return null;

		}

//		if (connection != null) {
//			System.out.println("You made it, take control your database now!");
//		} else {
//			System.out.println("Failed to make connection!");
//		}
	}

}