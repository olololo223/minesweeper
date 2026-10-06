package com.example.minesweeper.dao;

import com.example.minesweeper.db.DataSourceProvider;
import com.example.minesweeper.protocol.payload.GetFriendsResponse;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Заявки в друзья и дружба.
 *
 * Между двумя игроками всегда не больше одной строки: (user_id) — инициатор,
 * (friend_id) — адресат. Направление задаёт status=pending, а accepted —
 * это уже дружба, у неё направления нет.
 */
public class FriendshipDao {

    /** Статус связи между двумя игроками (с точки зрения from). */
    public enum Relation {
        NONE,        // никак не связаны
        SELF,        // это он сам
        FRIEND,      // уже друзья
        PENDING_OUT, // from отправил заявку to
        PENDING_IN   // to отправил заявку from
    }

    /** Узнать, кто такой username и как он связан с fromUserId. */
    public RelationAndUser findUser(String username, long fromUserId)
            throws SQLException {
        String sqlUser = "SELECT id, username FROM users WHERE username = ?";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sqlUser)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;      // пользователь не найден
                long id = rs.getLong("id");
                String name = rs.getString("username");

                Relation rel;
                if (id == fromUserId) {
                    rel = Relation.SELF;
                } else {
                    rel = findRelation(c, fromUserId, id);
                }
                return new RelationAndUser(id, name, rel);
            }
        }
    }

    /** Ищет любую запись между a и b в любом направлении. */
    private Relation findRelation(Connection c, long fromUserId, long targetUserId)
            throws SQLException {
        String sql =
                "SELECT user_id, friend_id, status FROM friendships " +
                        "WHERE (user_id = ? AND friend_id = ?) " +
                        "   OR (user_id = ? AND friend_id = ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, fromUserId);
            ps.setLong(2, targetUserId);
            ps.setLong(3, targetUserId);
            ps.setLong(4, fromUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Relation.NONE;
                long u = rs.getLong("user_id");
                String status = rs.getString("status");
                if ("accepted".equals(status)) return Relation.FRIEND;
                // pending
                return (u == fromUserId) ? Relation.PENDING_OUT : Relation.PENDING_IN;
            }
        }
    }

    /**
     * Создаёт заявку from → to.
     * Проверяет в коде, что связи нет ни в одном направлении: уникального
     * индекса на пару в схеме нет (MySQL требует функциональный индекс).
     */
    public boolean createRequest(long fromUserId, long toUserId) throws SQLException {
        try (Connection c = DataSourceProvider.get().getConnection()) {
            if (findRelation(c, fromUserId, toUserId) != Relation.NONE) {
                return false;
            }
            String sql = "INSERT INTO friendships(user_id, friend_id, status) " +
                    "VALUES (?, ?, 'pending')";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, fromUserId);
                ps.setLong(2, toUserId);
                ps.executeUpdate();
            }
            return true;
        } catch (SQLIntegrityConstraintViolationException dup) {
            // Гонка: параллельный канал успел вставить раньше. Уникальный индекс
            // uk_friendship_pair (LEAST/GREATEST) не дал создать вторую строку —
            // для нас это не ошибка, а ответ «связь уже существует».
            return false;
        }
    }

    /** Принять входящую заявку: status='accepted'. */
    public boolean accept(long requesterUserId, long currentUserId) throws SQLException {
        String sql =
                "UPDATE friendships SET status = 'accepted' " +
                        "WHERE user_id = ? AND friend_id = ? AND status = 'pending'";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, requesterUserId);
            ps.setLong(2, currentUserId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Отклонить входящую заявку — удаляем строку. */
    public boolean decline(long requesterUserId, long currentUserId) throws SQLException {
        String sql = "DELETE FROM friendships " +
                "WHERE user_id = ? AND friend_id = ? AND status = 'pending'";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, requesterUserId);
            ps.setLong(2, currentUserId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Удалить друга — удаляем только принятую связь, в любом направлении. */
    public boolean remove(long currentUserId, long otherUserId) throws SQLException {
        String sql =
                "DELETE FROM friendships " +
                        "WHERE status = 'accepted' AND " +
                        "((user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?))";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, currentUserId);
            ps.setLong(2, otherUserId);
            ps.setLong(3, otherUserId);
            ps.setLong(4, currentUserId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Отменить свою исходящую заявку.
     * В отличие от decline (её снимает адресат), здесь инициатор — текущий игрок.
     */
    public boolean cancelOutgoing(long currentUserId, long otherUserId) throws SQLException {
        String sql = "DELETE FROM friendships " +
                "WHERE user_id = ? AND friend_id = ? AND status = 'pending'";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, currentUserId);
            ps.setLong(2, otherUserId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Все связи пользователя (принятые + входящие + исходящие). */
    public List<GetFriendsResponse.FriendInfo> listAll(long userId) throws SQLException {
        String sql =
                "SELECT u.id AS uid, u.username, " +
                        "       f.user_id, f.status, f.created_at " +
                        "FROM friendships f " +
                        "JOIN users u ON u.id = CASE " +
                        "       WHEN f.user_id = ? THEN f.friend_id " +
                        "       ELSE f.user_id END " +
                        "WHERE f.user_id = ? OR f.friend_id = ? " +
                        "ORDER BY f.created_at DESC";
        List<GetFriendsResponse.FriendInfo> out = new ArrayList<>();
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setLong(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long otherId = rs.getLong("uid");
                    String otherName = rs.getString("username");
                    String rawStatus = rs.getString("status");
                    long initiator = rs.getLong("user_id");

                    String status;
                    if ("accepted".equals(rawStatus)) {
                        status = "ACCEPTED";
                    } else {
                        // pending — определяем направление
                        status = (initiator == userId) ? "PENDING_OUT" : "PENDING_IN";
                    }

                    out.add(new GetFriendsResponse.FriendInfo(
                            otherId, otherName, status,
                            rs.getTimestamp("created_at").toLocalDateTime().toString()));
                }
            }
        }
        return out;
    }

    /** Сколько входящих заявок у пользователя. */
    public int countIncoming(long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM friendships " +
                "WHERE friend_id = ? AND status = 'pending'";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Возвращаемый объект findUser. */
    public static class RelationAndUser {
        public final long userId;
        public final String username;
        public final Relation relation;

        public RelationAndUser(long userId, String username, Relation relation) {
            this.userId = userId;
            this.username = username;
            this.relation = relation;
        }
    }

    /** Проверяет, что пользователи — принятые друзья. */
    public boolean areFriends(long userA, long userB) throws SQLException {
        String sql =
                "SELECT COUNT(*) FROM friendships " +
                        "WHERE status = 'accepted' " +
                        "  AND ((user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?))";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userA);
            ps.setLong(2, userB);
            ps.setLong(3, userB);
            ps.setLong(4, userA);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }
}
