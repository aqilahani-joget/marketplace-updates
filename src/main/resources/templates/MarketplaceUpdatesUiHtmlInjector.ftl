<div id="update" class="pluginList_container">
    <div id="main-body-content-filter">
    </div>

    <table id="pluginList4"></table>
</div>



<script>
(function() {

        const $tabs = $("#pluginstab ul"); // tab headers
        const $updateTab = $tabs.find("a[href='#update']");
        const $configurablePanel = $("#configurableplugins");

        // Add Update tab header if it doesn't exist
        if ($tabs.length && !$updateTab.length) {
            const tabHeader = `
                <li role="tab" class="ui-tabs-tab ui-corner-top ui-state-default ui-tab">
                    <a href="#update" class="ui-tabs-anchor">
                        <span>Update(${pluginsSize})</span>
                    </a>
                </li>`;
            $tabs.append(tabHeader);
        }

        // Move the existing Update panel after Configurable Plugins panel
        const $updatePanel = $("#update");
        if ($updatePanel.length && $updatePanel.next()[0] !== $configurablePanel.next()[0]) {
            $updatePanel.insertAfter($configurablePanel);
        }
        // Create JsonTable instance for Update panel
        var JsonDataTable4 = new JsonTable();
        JsonDataTable4.divToUpdate = "pluginList4";
        JsonDataTable4.url = "/jw/web/json/plugin/org.joget.marketplace.MarketplaceUpdatesPlugin/service";
        JsonDataTable4.rowsPerPage = 15;
        JsonDataTable4.width = "100%";
        JsonDataTable4.sort = "name";
        JsonDataTable4.desc = false;
        JsonDataTable4.columns = [
            { key: 'label', label: 'Plugin Name', sortable: false, width: 180, relaxed: true},
            { key: 'description', label: 'Description', sortable: false, width: 300 },
            { key: 'version', label: 'Installed Version', sortable: false, width: 140 },
            { key: 'latestVersion', label: 'Latest Version', sortable: false, width: 140 },
        ];
        JsonDataTable4.checkbox = true;
        JsonDataTable4.buttons = null;
        JsonDataTable4.init();

})();
</script>
