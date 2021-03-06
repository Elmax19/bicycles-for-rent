package com.epam.jwd.rent.dao.impl;

import com.epam.jwd.rent.dao.CommonDao;
import com.epam.jwd.rent.exception.NoSuchEntityException;
import com.epam.jwd.rent.model.Bicycle;
import com.epam.jwd.rent.model.Order;
import com.epam.jwd.rent.model.User;
import com.epam.jwd.rent.model.OrderFactory;
import com.epam.jwd.rent.model.UserFactory;
import com.epam.jwd.rent.pool.ConnectionPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Order data access class
 *
 * @author Elmax19
 * @version 1.0
 */
public class OrderDao implements CommonDao<Order> {
    /**
     * variables of SQL code to work with database
     */
    private static final String FIND_ALL_ORDERS_SQL = "select * from orders";
    private static final String FIND_ORDER_BY_ID_SQL = "select * from orders where id=";
    private static final String CREATE_NEW_ORDER_SQL = "insert into orders value(?,?,?,?,?,?)";
    private static final String SET_STATUS_SQL = "update orders set status=? where id=?";
    private static final String FIND_ORDERS_FOR_PAGE_SQL = "select * from orders order by ";
    private static final String GET_COUNT_OF_ORDERS_SQL = "select count(id) AS count from orders";
    /**
     * variable of database count column name
     */
    private static final String COUNT_COLUMN_NAME = "count";
    /**
     * link at {@link ConnectionPool} to get connections
     */
    private static final ConnectionPool POOL = ConnectionPool.getInstance();
    /**
     * {@link OrderDao} class {@link Logger}
     */
    private static final Logger LOGGER = LogManager.getLogger(OrderDao.class);
    /**
     * {@link ReentrantLock} to lock methods
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * method that sets {@link Order#count} as count of orders in database
     */
    public void initCountOfOrders() {
        Optional<List<Order>> orders = findAll();
        if (orders.isPresent()) {
            if (orders.get().size() > 0) {
                Order.count = orders.map(orderList -> orderList.get(orderList.size() - 1).getId()).orElse(0);
            }
        }
    }

    /**
     * {@link CommonDao} method realisation
     * @return list of all orders
     * @see CommonDao
     */
    @Override
    public Optional<List<Order>> findAll() {
        try (final Connection conn = POOL.retrieveConnection();
             final Statement statement = conn.createStatement();
             final ResultSet resultSet = statement.executeQuery(FIND_ALL_ORDERS_SQL)) {
            List<Order> orders = new ArrayList<>();
            while (resultSet.next()) {
                orders.add(readOrder(resultSet));
            }
            return Optional.of(orders);
        } catch (SQLException e) {
            LOGGER.error("SQLException at method findAll: " + e.getSQLState());
            return Optional.empty();
        }
    }

    /**
     * {@link CommonDao} method realisation
     * @param n count of raws
     * @return count of pages
     * @see CommonDao
     */
    @Override
    public int getCountOfPages(int n) {
        try (final Connection con = POOL.retrieveConnection();
             final Statement statement = con.createStatement();
             final ResultSet resultSet = statement.executeQuery(GET_COUNT_OF_ORDERS_SQL)) {
            if(resultSet.next()) {
                return (resultSet.getInt(COUNT_COLUMN_NAME) + n - 1) / n;
            }
        } catch (SQLException e) {
            LOGGER.error("SQLException at method getCount: " + e.getSQLState());
        }
        return 0;
    }

    /**
     * {@link CommonDao} method realisation
     * @param column column name of sorting
     * @param n page number
     * @return list of sought Orders
     * @see CommonDao
     */
    @Override
    public Optional<List<Order>> findByPageNumber(String column, int n) {
        try (final Connection conn = POOL.retrieveConnection();
             final Statement statement = conn.createStatement();
             final ResultSet resultSet = statement.executeQuery(FIND_ORDERS_FOR_PAGE_SQL + column + " LIMIT " + 7 * (n - 1) + ',' + 7)) {
            List<Order> orders = new ArrayList<>();
            while (resultSet.next()) {
                orders.add(readOrder(resultSet));
            }
            return Optional.of(orders);
        } catch (SQLException e) {
            LOGGER.error("SQLException at method findAll: " + e.getSQLState());
            return Optional.empty();
        }
    }

    /**
     * method to create new Order
     * @param order new Order
     * @return Order if it has been created, otherwise return empty optional
     */
    public Optional<Order> create(Order order) {
        lock.lock();
        try (final Connection conn = POOL.retrieveConnection();
             final PreparedStatement preparedStatement = conn.prepareStatement(CREATE_NEW_ORDER_SQL)) {
            preparedStatement.setInt(1, order.getId());
            preparedStatement.setInt(2, order.getUser_id());
            preparedStatement.setInt(3, order.getBicycle_id());
            preparedStatement.setInt(4, order.getHours());
            preparedStatement.setString(5, order.getStatus());
            preparedStatement.setDate(6, Date.valueOf(order.getDate()));
            preparedStatement.execute();
            return Optional.of(order);
        } catch (SQLException e) {
            LOGGER.error("SQLException at method create: " + e.getSQLState());
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@link CommonDao} method realisation
     * @param id place in database
     * @return Order if it exists, otherwise return empty optional
     * @see CommonDao
     */
    @Override
    public Optional<Order> findById(int id) {
        try (final Connection conn = POOL.retrieveConnection();
             final Statement statement = conn.createStatement();
             final ResultSet resultSet = statement.executeQuery(FIND_ORDER_BY_ID_SQL + id)) {
            if (resultSet.next()) {
                return Optional.of(readOrder(resultSet));
            }
        } catch (SQLException e) {
            LOGGER.error("SQLException at method findById: " + e.getSQLState());
        }
        return Optional.empty();
    }

    /**
     * method to crate {@link Order} object from ResultSet
     * @param resultSet result of database operation
     * @return {@link OrderFactory} creation method result
     */
    private Order readOrder(ResultSet resultSet) throws SQLException {
        return OrderFactory.getInstance().create(resultSet);
    }

    /**
     * method to find User name by his/her id
     * @param userId User number in database
     * @return sought User name
     * @throws SQLException if something goes wrong
     */
    public String findUserName(int userId) throws SQLException {
        Optional<List<User>> users = new UserDao().findAll();
        if (users.isPresent()) {
            Optional<User> user = users.get().stream().filter(person -> person.getId() == userId).findFirst();
            if (user.isPresent()) {
                return user.get().getLogin();
            }
        }
        throw new NoSuchEntityException("Unknown user id(" + userId + ')');
    }

    /**
     * method to find Bicycle model by its id
     * @param bicycleId Bicycle number in database
     * @return sought Bicycle model name
     * @throws SQLException if something goes wrong
     */
    public String findBicycleModel(int bicycleId) throws SQLException {
        Optional<List<Bicycle>> bicycles = new BicycleDao().findAll();
        if (bicycles.isPresent()) {
            Optional<Bicycle> currentBicycle = bicycles.get().stream().filter(bicycle -> bicycle.getId() == bicycleId).findFirst();
            if (currentBicycle.isPresent()) {
                return currentBicycle.get().getModel();
            }
        }
        throw new NoSuchEntityException("Unknown bicycle model id(" + bicycleId + ')');
    }

    /**
     * method to find all orders a for specific User
     * @param name User name
     * @return list of his/her Orders
     */
    public Optional<List<Order>> findAllOrdersByUserName(String name) {
        Optional<List<Order>> orders = findAll();
        return orders.map(orderList -> orderList.stream()
                .filter(order -> {
                    try {
                        return findUserName(order.getUser_id()).equals(name);
                    } catch (SQLException | NoSuchEntityException e) {
                        LOGGER.error("Fail to find orders by user name: " + e.getLocalizedMessage());
                        return false;
                    }
                }).collect(Collectors.toList()));
    }

    /**
     * method to make Order accepted or canceled
     * @param id Order number in database
     * @param status accepted | canceled
     */
    public void changeStatus(int id, String status) {
        try (final Connection conn = POOL.retrieveConnection();
             final PreparedStatement preparedStatement = conn.prepareStatement(SET_STATUS_SQL)) {
            preparedStatement.setString(1, status);
            preparedStatement.setInt(2, id);
            preparedStatement.execute();
        } catch (SQLException e) {
            LOGGER.error("SQLException at changing status: " + e.getSQLState());
        }
    }
}
