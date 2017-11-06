package com.yixin.facerecognition.model;

import java.io.Serializable;

/**
 * Created by Administrator on 2017/10/16.
 */

public class UserModel implements Serializable {

    private int userId;

    private String userImg;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUserImg() {
        return userImg;
    }

    public void setUserImg(String userImg) {
        this.userImg = userImg;
    }
}
