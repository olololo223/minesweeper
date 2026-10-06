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

        // Топ-5 по каждой комбинации «сложность × режим»
        model.addAttribute("topEasyClassic", dao.topByDifficulty("EASY", "CLASSIC"));
        model.addAttribute("topEasyTimed", dao.topByDifficulty("EASY", "TIMED"));
        model.addAttribute("topMediumClassic", dao.topByDifficulty("MEDIUM", "CLASSIC"));
        model.addAttribute("topMediumTimed", dao.topByDifficulty("MEDIUM", "TIMED"));
        model.addAttribute("topHardClassic", dao.topByDifficulty("HARD", "CLASSIC"));
        model.addAttribute("topHardTimed", dao.topByDifficulty("HARD", "TIMED"));

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
                          @RequestParam(required = false) String mode,
                          @RequestParam(required = false) String sort,
                          @RequestParam(required = false) String dir,
                          Model model) {
        model.addAttribute("records", dao.listRecords(limit, mode, sort, dir));
        model.addAttribute("limit", limit);
        model.addAttribute("mode", mode == null ? "ALL" : mode);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        return "records";
    }

    @PostMapping("/records/delete")
    public String deleteRecord(@RequestParam long id,
                               @RequestParam(required = false) String mode,
                               RedirectAttributes ra) {
        dao.deleteRecord(id);
        dao.logAction("RECORD_DELETE", "record#" + id, null);
        ra.addFlashAttribute("msg", "Запись удалена");
        return "redirect:/records" + appendMode(mode);
    }

    // ===== РЕЙТИНГ =====

    @GetMapping("/leaderboard")
    public String leaderboard(@RequestParam(defaultValue = "EASY") String diff,
                              @RequestParam(required = false) String mode,
                              @RequestParam(required = false) String sort,
                              @RequestParam(required = false) String dir,
                              Model model) {
        String m = (mode == null || mode.isBlank()) ? "ALL" : mode;
        model.addAttribute("diff", diff);
        model.addAttribute("mode", m);
        model.addAttribute("entries", dao.listLeaderboard(diff, m, sort, dir));
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        return "leaderboard";
    }

    @PostMapping("/leaderboard/update")
    public String updateLeaderboard(@RequestParam long id,
                                    @RequestParam int seconds,
                                    @RequestParam String diff,
                                    @RequestParam(required = false) String mode,
                                    RedirectAttributes ra) {
        dao.updateLeaderboardTime(id, seconds);
        dao.logAction("LB_UPDATE", "lb#" + id,
                diff + " = " + seconds + " сек.");
        ra.addFlashAttribute("msg", "Время обновлено");
        return "redirect:/leaderboard?diff=" + diff + appendModeQuery(mode);
    }

    @PostMapping("/leaderboard/delete")
    public String deleteLeaderboardEntry(@RequestParam long id,
                                         @RequestParam String diff,
                                         @RequestParam(required = false) String mode,
                                         RedirectAttributes ra) {
        dao.deleteLeaderboardEntry(id);
        dao.logAction("LB_DELETE", "lb#" + id, diff);
        ra.addFlashAttribute("msg", "Запись удалена");
        return "redirect:/leaderboard?diff=" + diff + appendModeQuery(mode);
    }

    @PostMapping("/leaderboard/clear")
    public String clearLeaderboard(@RequestParam String diff,
                                   @RequestParam(required = false) String mode,
                                   RedirectAttributes ra) {
        dao.clearLeaderboard(diff, mode);
        String details = "очистка рейтинга " + diff
                + (mode != null && !mode.isBlank() && !"ALL".equals(mode)
                ? " / " + mode : " (все режимы)");
        dao.logAction("LB_CLEAR", diff, details);
        ra.addFlashAttribute("msg", details);
        return "redirect:/leaderboard?diff=" + diff + appendModeQuery(mode);
    }

    /** "?mode=TIMED" для /records, либо пустая строка. */
    private static String appendMode(String mode) {
        return (mode == null || mode.isBlank() || "ALL".equals(mode))
                ? "" : "?mode=" + mode;
    }

    /** "&mode=TIMED" для /leaderboard?diff=..., либо пустая строка. */
    private static String appendModeQuery(String mode) {
        return (mode == null || mode.isBlank() || "ALL".equals(mode))
                ? "" : "&mode=" + mode;
    }

    @GetMapping("/audit")
    public String audit(@RequestParam(defaultValue = "200") int limit, Model model) {
        model.addAttribute("actions", dao.listActions(limit));
        model.addAttribute("limit", limit);
        return "audit";
    }

    @GetMapping("/mp")
    public String mpLeaderboard(Model model) {
        model.addAttribute("leaderboard", dao.listMpLeaderboard());
        return "mp_leaderboard";
    }

    @GetMapping("/mp/records")
    public String mpRecords(@RequestParam(defaultValue = "100") int limit,
                            Model model) {
        model.addAttribute("records", dao.listMpRecords(limit));
        model.addAttribute("limit", limit);
        return "mp_records";
    }

    @PostMapping("/mp/records/delete")
    public String deleteMpRecord(@RequestParam long id, RedirectAttributes ra) {
        dao.deleteMpRecord(id);
        dao.logAction("MP_RECORD_DELETE", "mp#" + id, null);
        ra.addFlashAttribute("msg", "Запись удалена");
        return "redirect:/mp/records";
    }
}