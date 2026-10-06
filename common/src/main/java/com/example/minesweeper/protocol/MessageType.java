package com.example.minesweeper.protocol;

import com.example.minesweeper.protocol.payload.*;

public enum MessageType {
    REGISTER_REQUEST(RegisterRequest.class),
    REGISTER_RESPONSE(RegisterResponse.class),

    LOGIN_REQUEST(LoginRequest.class),
    LOGIN_RESPONSE(LoginResponse.class),

    CHANGE_PASSWORD_REQUEST(ChangePasswordRequest.class),      // ← новое
    CHANGE_PASSWORD_RESPONSE(ChangePasswordResponse.class),    // ← новое

    SUBMIT_SCORE_REQUEST(SubmitScoreRequest.class),
    SUBMIT_SCORE_RESPONSE(SubmitScoreResponse.class),

    LEADERBOARD_REQUEST(LeaderboardRequest.class),
    LEADERBOARD_RESPONSE(LeaderboardResponse.class),

    FRIEND_INVITE_REQUEST(FriendInviteRequest.class),
    FRIEND_INVITE_RESPONSE(FriendInviteResponse.class),
    FRIEND_INVITE_PUSH(FriendInvitePush.class),

    PING(null),
    PONG(null),
    ERROR(LoginResponse.class),   // сейчас ERROR шлёт LoginResponse, оставим так

    // Добавлены в конец, чтобы не сдвигать ordinal'ы существующих типов
    MY_STATS_REQUEST(MyStatsRequest.class),
    MY_STATS_RESPONSE(MyStatsResponse.class),

    // Друзья (тоже в конец — ordinal'ы всех прежних типов не меняются)
    FIND_USER_REQUEST(FindUserRequest.class),
    FIND_USER_RESPONSE(FindUserResponse.class),

    ADD_FRIEND_REQUEST(AddFriendRequest.class),
    ADD_FRIEND_RESPONSE(AddFriendResponse.class),

    ACCEPT_FRIEND_REQUEST(AcceptFriendRequest.class),
    ACCEPT_FRIEND_RESPONSE(AcceptFriendResponse.class),

    DECLINE_FRIEND_REQUEST(DeclineFriendRequest.class),
    DECLINE_FRIEND_RESPONSE(DeclineFriendResponse.class),

    REMOVE_FRIEND_REQUEST(RemoveFriendRequest.class),
    REMOVE_FRIEND_RESPONSE(RemoveFriendResponse.class),

    GET_FRIENDS_REQUEST(GetFriendsRequest.class),
    GET_FRIENDS_RESPONSE(GetFriendsResponse.class),

    COUNT_FRIEND_REQUESTS_REQUEST(CountFriendRequestsRequest.class),
    COUNT_FRIEND_REQUESTS_RESPONSE(CountFriendRequestsResponse.class),

    // Push от сервера (в конец — ordinal'ы прежних типов не меняются)
    FRIEND_REQUEST_PUSH(FriendPush.class),
    FRIEND_ACCEPTED_PUSH(FriendPush.class),

    // Мультиплеер: комнаты (тоже в конец)
    CREATE_ROOM_REQUEST(CreateRoomRequest.class),
    CREATE_ROOM_RESPONSE(CreateRoomResponse.class),

    JOIN_ROOM_REQUEST(JoinRoomRequest.class),
    JOIN_ROOM_RESPONSE(JoinRoomResponse.class),

    LEAVE_ROOM_REQUEST(LeaveRoomRequest.class),
    LEAVE_ROOM_RESPONSE(LeaveRoomResponse.class),

    LIST_ROOMS_REQUEST(ListRoomsRequest.class),
    LIST_ROOMS_RESPONSE(ListRoomsResponse.class),

    START_ROOM_REQUEST(StartRoomRequest.class),
    START_ROOM_RESPONSE(StartRoomResponse.class),

    DIG_REQUEST(DigRequest.class),
    FLAG_REQUEST(FlagRequest.class),

    ROOM_UPDATE_PUSH(RoomUpdatePush.class),
    ROOM_FINISHED_PUSH(RoomFinishedPush.class),

    MP_LEADERBOARD_REQUEST(MpLeaderboardRequest.class),
    MP_LEADERBOARD_RESPONSE(MpLeaderboardResponse.class),

    MP_STATS_REQUEST(MpStatsRequest.class),
    MP_STATS_RESPONSE(MpStatsResponse.class),

    // Только для тестов: включается флагом -Ddebug.allowMines=true
    DEBUG_GET_MINES_REQUEST(DebugMinesRequest.class),
    DEBUG_GET_MINES_RESPONSE(DebugMinesResponse.class);

    private final Class<?> payloadClass;

    MessageType(Class<?> payloadClass) {
        this.payloadClass = payloadClass;
    }

    public Class<?> getPayloadClass() {
        return payloadClass;
    }
}