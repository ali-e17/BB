package com.example.bb

import java.io.Serializable

data class Student(
    var id: String,
    var firstName: String,
    var lastName: String,
    var studentCode: String,
    var level: String,
    var phoneNumber: String,
    var registrationDate: String,
    var isActive: Boolean = true,
    var avatarResId: Int
) : Serializable {
    val fullName: String
        get() = "$firstName $lastName"
}