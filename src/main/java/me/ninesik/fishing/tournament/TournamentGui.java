package me.ninesik.fishing.tournament;

import me.ninesik.fishing.gui.AbstractGui;
import me.ninesik.fishing.gui.GuiItems;
import me.ninesik.fishing.gui.GuiLayout;
import me.ninesik.fishing.gui.GuiTexts;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 대회 GUI.
 * 진행 중인 대회와 대기 중인 대회 목록을 표시하고, 클릭으로 참가/정보 확인.
 */
public class TournamentGui extends AbstractGui {

    private static final int ROWS = 6;
    private static final int CONTENT_START = 9;
    private static final int CONTENT_END = 44;

    private final TournamentManager tournamentManager;
    private int page = 0;

    public TournamentGui(Player player, TournamentManager tournamentManager) {
        super(player, ROWS, GuiTexts.text("tournament.title"));
        this.tournamentManager = tournamentManager;
    }

    @Override
    public void initialize() {
        inventory.clear();
        renderContent();
        renderBottom();
    }

    private void renderContent() {
        List<Tournament> all = new ArrayList<>(tournamentManager.getTournaments());
        int pageSize = CONTENT_END - CONTENT_START + 1;
        int totalPages = Math.max(1, (all.size() + pageSize - 1) / pageSize);
        page = Math.min(page, totalPages - 1);

        int start = page * pageSize;
        int end = Math.min(start + pageSize, all.size());

        for (int i = start; i < end; i++) {
            Tournament tournament = all.get(i);
            ItemStack item = buildTournamentIcon(tournament);
            int slot = CONTENT_START + (i - start);
            setItem(slot, item);
        }
    }

    private void renderBottom() {
        int pageSize = CONTENT_END - CONTENT_START + 1;
        // 이전/다음 화살표 + 닫기 + 메인으로는 모든 6줄 GUI가 같은 자리를 쓴다 (GuiLayout).
        GuiLayout.renderFooter(getInventory(), page,
                GuiLayout.totalPages(tournamentManager.getTournaments().size(), pageSize));
    }

    private ItemStack buildTournamentIcon(Tournament tournament) {
        Material material = tournament.isRunning() ? Material.FISHING_ROD : Material.CLOCK;
        String name = ChatColor.YELLOW + ChatColor.stripColor(tournament.getName());

        // 로어 구성 자체가 config다 — 어드민이 줄을 지우거나 순서를 바꿀 수 있다.
        Map<String, String> ph = new HashMap<>();
        ph.put("id", tournament.getId());
        ph.put("type", String.valueOf(tournament.getType()));
        ph.put("target_grade", tournament.getTargetGrade() == null
                ? "" : tournament.getTargetGrade().toUpperCase());
        ph.put("duration", String.valueOf(tournament.getDurationMinutes()));
        ph.put("entry_fee", String.valueOf(tournament.getEntryFee()));
        ph.put("schedule_day", String.valueOf(tournament.getScheduleDay()));
        ph.put("schedule_time", String.valueOf(tournament.getScheduleTime()));

        // 조건부 줄은 별도 리스트로 두고 순서대로 이어 붙인다. 한 리스트에서
        // 빼내는 방식은 같은 문구가 다른 줄에 있으면 엉뚱한 줄이 지워진다.
        List<String> lore = new ArrayList<>(GuiTexts.lines("tournament.entry.lore", ph));
        if (tournament.getTargetGrade() != null) {
            lore.addAll(GuiTexts.lines("tournament.entry.target-grade-lore", ph));
        }
        lore.addAll(GuiTexts.lines("tournament.entry.detail-lore", ph));
        lore.addAll(tournament.isRunning()
                ? GuiTexts.lines("tournament.entry.running-lore", ph)
                : GuiTexts.lines("tournament.entry.waiting-lore", ph));
        if (!tournament.isRunning() && tournament.isAutoStart()) {
            lore.addAll(GuiTexts.lines("tournament.entry.auto-start-lore", ph));
        }

        return GuiItems.createIcon(material, name, lore);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        if (slot == GuiLayout.SLOT_PREV_PAGE && page > 0) {
            page--;
            refresh();
            return;
        }
        if (slot == GuiLayout.SLOT_NEXT_PAGE) {
            page++;
            refresh();
            return;
        }
        if (slot == GuiLayout.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == GuiLayout.SLOT_BACK_MAIN) {
            me.ninesik.fishing.gui.MainGui.open(player);
            return;
        }

        if (slot >= CONTENT_START && slot <= CONTENT_END) {
            List<Tournament> all = new ArrayList<>(tournamentManager.getTournaments());
            int pageSize = CONTENT_END - CONTENT_START + 1;
            int index = page * pageSize + (slot - CONTENT_START);
            if (index < 0 || index >= all.size()) return;

            Tournament tournament = all.get(index);
            if (tournament.isRunning()) {
                player.closeInventory();
                tournamentManager.join(player, tournament.getId());
            } else {
                player.sendMessage(GuiTexts.text("tournament.not-running"));
            }
        }
    }
}
