package com.example.minesweeper.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminController {

    private final AdminDao dao;

    public AdminController(AdminDao dao) {
        this.dao = dao;
    }

    // ===== ГЛАВНАЯ =====

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("stats", dao.stats());
        model.addAttribute("topEasy", dao.topByDifficulty("EASY"));
        model.addAttribute("topMedium", dao.topByDifficulty("MEDIUM"));
        model.addAttribute("topHard", dao.topByDifficulty("HARD"));
        return "index";
    }

    // ===== ПОЛЬЗОВАТЕЛИ =====

    @GetMapping("/users")
    public String users(@RequestParam(required = false) String sort,
                        @RequestParam(required = false) String dir,
                        Model model) {
        model.addAttribute("users", dao.listUsers(sort, dir));
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        return "users";
    }

    @PostMapping("/users/delete")
    public String deleteUser(@RequestParam long id, RedirectAttributes ra) {
        dao.deleteUser(id);
        dao.logAction("USER_DELETE", "user#" + id, null);
        ra.addFlashAttribute("msg", "Пользователь удалён (ID " + id + ")");
        return "redirect:/users";
    }

    @PostMapping("/users/role")
    public String updateRole(@RequestParam long id,
                             @RequestParam String role,
                             RedirectAttributes ra) {
        dao.updateUserRole(id, role);
        dao.logAction("USER_ROLE_CHANGE", "user#" + id, "новая роль: " + role);
        ra.addFlashAttribute("msg", "Роль обновлена");
        return "redirect:/users";
    }

    @GetMapping("/users/{id}/records")
    public String userRecords(@PathVariable long id, Model model) {
        model.addAttribute("records", dao.listRecordsByUser(id));
        model.addAttribute("userId", id);
        return "user_records";
    }

    // ===== ЗАПИСИ =====

    @GetMapping("/records")
    public String records(@RequestParam(defaultValue = "100") int limit,
                          @RequestParam(required = false) String sort,
                          @RequestParam(required = false) String dir,
                          Model model) {
        model.addAttribute("records", dao.listRecords(limit, sort, dir));
        model.addAttribute("limit", limit);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        return "records";
    }

    @PostMapping("/records/delete")
    public String deleteRecord(@RequestParam long id, RedirectAttributes ra) {
        dao.deleteRecord(id);
        dao.logAction("RECORD_DELETE", "record#" + id, null);
        ra.addFlashAttribute("msg", "Запись удалена");
        return "redirect:/records";
    }

    // ===== РЕЙТИНГ =====

    @GetMapping("/leaderboard")
    public String leaderboard(@RequestParam(defaultValue = "EASY") String diff,
                              @RequestParam(required = false) String sort,
                              @RequestParam(required = false) String dir,
                              Model model) {
        model.addAttribute("diff", diff);
        model.addAttribute("entries", dao.listLeaderboard(diff, sort, dir));
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        return "leaderboard";
    }

    @PostMapping("/leaderboard/update")
    public String updateLeaderboard(@RequestParam long id,
                                    @RequestParam int seconds,
                                    @RequestParam String diff,
                                    RedirectAttributes ra) {
        dao.updateLeaderboardTime(id, seconds);
        dao.logAction("LB_UPDATE", "lb#" + id,
                diff + " = " + seconds + " сек.");
        ra.addFlashAttribute("msg", "Время обновлено");
        return "redirect:/leaderboard?diff=" + diff;
    }

    @PostMapping("/leaderboard/delete")
    public String deleteLeaderboardEntry(@RequestParam long id,
                                         @RequestParam String diff,
                                         RedirectAttributes ra) {
        dao.deleteLeaderboardEntry(id);
        dao.logAction("LB_DELETE", "lb#" + id, diff);
        ra.addFlashAttribute("msg", "Запись удалена");
        return "redirect:/leaderboard?diff=" + diff;
    }

    @PostMapping("/leaderboard/clear")
    public String clearLeaderboard(@RequestParam String diff,
                                   RedirectAttributes ra) {
        dao.clearLeaderboard(diff);
        dao.logAction("LB_CLEAR", diff, "очистка рейтинга сложности " + diff);
        ra.addFlashAttribute("msg", "Рейтинг " + diff + " очищен");
        return "redirect:/leaderboard?diff=" + diff;
    }

    @GetMapping("/audit")
    public String audit(@RequestParam(defaultValue = "200") int limit, Model model) {
        model.addAttribute("actions", dao.listActions(limit));
        model.addAttribute("limit", limit);
        return "audit";
    }
}