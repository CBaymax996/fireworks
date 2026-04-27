package site.hanabii.fireworks.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import site.hanabii.fireworks.domain.User
import site.hanabii.fireworks.service.UserService

@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {

    @GetMapping
    fun list(): List<User> = userService.list()

    @PostMapping
    fun create(
        @RequestParam name: String,
        @RequestParam email: String
    ): User = userService.create(name, email)
}
