package com.example.client_mobile.protocol.payload;

public class FindUserResponse {
    public boolean found;
    public String message;          // причина "не найдено", если found=false
    public long userId;
    public String username;
    public String relation;         // NONE / PENDING_OUT / PENDING_IN / FRIEND / SELF

    public FindUserResponse() {}

    @Override
    public String toString() {
        return "FindUserResponse{found=" + found + ", username='" + username
                + "', relation='" + relation + "'}";
    }
}
