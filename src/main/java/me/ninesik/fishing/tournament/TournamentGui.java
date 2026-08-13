package me.ninesik.fishing.tournament;

import me.ninesik.fishing.gui.AbstractGui;
import me.ninesik.fishing.gui.GuiItems;
import me.ninesik.fishing.gui.GuiLayout;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

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
        super(player, ROWS, ChatColor.GOLD + "낚시 대회");
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

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "ID: " + ChatColor.WHITE + tournament.getId());
        lore.add(ChatColor.GRAY + "유형: " + ChatColor.WHITE + tournament.getType());
        if (tournament.getTargetGrade() != null) {
            lore.add(ChatColor.GRAY + "목표 등급: " + ChatColor.WHITE + tournament.getTargetGrade().toUpperCase());
        }
        lore.add(ChatColor.GRAY + "진행 시간: " + ChatColor.WHITE + tournament.getDurationMinutes() + "분");
        lore.add(ChatColor.GRAY + "참가비: " + ChatColor.WHITE + tournament.getEntryFee());

        if (tournament.isRunning()) {
            lore.add(ChatColor.GREEN + "진행 중");
            lore.add(ChatColor.YELLOW + "좌클릭: 참가");
            lore.add(ChatColor.GRAY + "/fishing tournament join " + tournament.getId());
        } else {
            lore.add(ChatColor.GRAY + "대기 중");
            if (tournament.isAutoStart()) {
                lore.add(ChatColor.GRAY + "자동 시작: " + tournament.getScheduleDay() + " " + tournament.getScheduleTime());
            }
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
                player.sendMessage(ChatColor.GRAY + "해당 대회는 현재 대기 중입니다.");
            }
        }
    }
}
