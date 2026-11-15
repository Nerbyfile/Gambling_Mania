package com.example.my_application_sandbox;

public class Avatar {
    private static final int[] RES = {
            R.drawable.avatar_1, R.drawable.avatar_2, R.drawable.avatar_3, R.drawable.avatar_4,
            R.drawable.avatar_5, R.drawable.avatar_6, R.drawable.avatar_7, R.drawable.avatar_8
    };
    public static final int COUNT = RES.length;

    private Avatar() { }

    public static int res(int id) {
        return (id >= 0 && id < COUNT) ? RES[id] : RES[0];
    }
}
