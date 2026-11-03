/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.theme

// Pysanka: the theme editor is gone, so the element names need no translations
enum class FlorisImeUi(val elementName: String) {
    Root(
        elementName = "root",
    ),
    Window(
        elementName = "window",
    ),
    WindowInner(
        elementName = "window-inner",
    ),
    WindowMoveHandle(
        elementName = "window-move-handle",
    ),
    WindowResizeAction(
        elementName = "window-resize-action",
    ),
    WindowResizeHandle(
        elementName = "window-resize-handle",
    ),
    WindowResizeOverlayFixed(
        elementName = "winow-resize-overlay-fixed",
    ),
    FloatingDockToFixedIndicator(
        elementName = "floating-dock-to-fixed-indicator",
    ),

    Key(
        elementName = "key",
    ),
    KeyHint(
        elementName = "key-hint",
    ),
    KeyPopupBox(
        elementName = "key-popup-box",
    ),
    KeyPopupElement(
        elementName = "key-popup-element",
    ),
    KeyPopupExtendedIndicator(
        elementName = "key-popup-extended-indicator",
    ),

    ClipboardHeader(
        elementName = "clipboard-header",
    ),
    ClipboardHeaderButton(
        elementName = "clipboard-header-button",
    ),
    ClipboardHeaderText(
        elementName = "clipboard-header-text",
    ),
    ClipboardSubheader(
        elementName = "clipboard-subheader",
    ),
    ClipboardContent(
        elementName = "clipboard-content",
    ),
    ClipboardFilterRow(
        elementName = "clipboard-filter-row",
    ),
    ClipboardFilterChip(
        elementName = "clipboard-filter-chip",
    ),
    ClipboardFilterChipIcon(
        elementName = "clipboard-filter-chip-icon",
    ),
    ClipboardFilterChipText(
        elementName = "clipboard-filter-chip-text",
    ),
    ClipboardGrid(
        elementName = "clipboard-grid",
    ),
    ClipboardItem(
        elementName = "clipboard-item",
    ),
    ClipboardItemDescription(
        elementName = "clipboard-item-description",
    ),
    ClipboardItemPopup(
        elementName = "clipboard-item-popup",
    ),
    ClipboardItemTimestamp(
        elementName = "clipboard-item-timestamp",
    ),
    ClipboardItemActions(
        elementName = "clipboard-item-actions",
    ),
    ClipboardItemAction(
        elementName = "clipboard-item-action",
    ),
    ClipboardItemActionIcon(
        elementName = "clipboard-item-action-icon",
    ),
    ClipboardItemActionText(
        elementName = "clipboard-item-action-text",
    ),
    ClipboardClearAllDialog(
        elementName = "clipboard-clear-all-dialog",
    ),
    ClipboardClearAllDialogMessage(
        elementName = "clipboard-clear-all-dialog-message",
    ),
    ClipboardClearAllDialogButtons(
        elementName = "clipboard-clear-all-dialog-buttons",
    ),
    ClipboardClearAllDialogButton(
        elementName = "clipboard-clear-all-dialog-button",
    ),
    ClipboardHistoryDisabledTitle(
        elementName = "clipboard-history-disabled-title",
    ),
    ClipboardHistoryDisabledMessage(
        elementName = "clipboard-history-disabled-message",
    ),
    ClipboardHistoryDisabledButton(
        elementName = "clipboard-history-disabled-button",
    ),
    ClipboardHistoryLockedTitle(
        elementName = "clipboard-history-locked-title",
    ),
    ClipboardHistoryLockedMessage(
        elementName = "clipboard-history-locked-message",
    ),

    ExtractedLandscapeInputLayout(
        elementName = "extracted-landscape-input-layout",
    ),
    ExtractedLandscapeInputField(
        elementName = "extracted-landscape-input-field",
    ),
    ExtractedLandscapeInputAction(
        elementName = "extracted-landscape-input-action",
    ),

    GlideTrail(
        elementName = "glide-trail",
    ),

    IncognitoModeIndicator(
        elementName = "incognito-mode-indicator",
    ),

    InlineAutofillChip(
        elementName = "inline-autofill-chip",
    ),

    Media(
        elementName = "media",
    ),

    MediaEmojiSubheader(
        elementName = "media-emoji-subheader",
    ),
    MediaEmojiKey(
        elementName = "media-emoji-key",
    ),
    MediaEmojiKeyPopupBox(
        elementName = "media-emoji-key-popup-box",
    ),
    MediaEmojiKeyPopupElement(
        elementName = "media-emoji-key-popup-element",
    ),
    MediaEmojiKeyPopupExtendedIndicator(
        elementName = "media-emoji-key-popup-extended-indicator",
    ),
    MediaEmojiTab(
        elementName = "media-emoji-tab",
    ),

    MediaBottomRow(
        elementName = "media-bottom-row",
    ),
    MediaBottomRowButton(
        elementName = "media-bottom-row-button",
    ),

    OneHandedPanel(
        elementName = "one-handed-panel",
    ),
    OneHandedPanelButton(
        elementName = "one-handed-panel-button",
    ),

    Smartbar(
        elementName = "smartbar",
    ),
    SmartbarSharedActionsRow(
        elementName = "smartbar-shared-actions-row",
    ),
    SmartbarSharedActionsToggle(
        elementName = "smartbar-shared-actions-toggle",
    ),
    SmartbarExtendedActionsRow(
        elementName = "smartbar-extended-actions-row",
    ),
    SmartbarExtendedActionsToggle(
        elementName = "smartbar-extended-actions-toggle",
    ),
    SmartbarActionKey(
        elementName = "smartbar-action-key",
    ),

    SmartbarActionTile(
        elementName = "smartbar-action-tile",
    ),
    SmartbarActionTileIcon(
        elementName = "smartbar-action-tile-icon",
    ),
    SmartbarActionTileText(
        elementName = "smartbar-action-tile-text",
    ),
    SmartbarActionsOverflow(
        elementName = "smartbar-actions-overflow",
    ),
    SmartbarActionsOverflowCustomizeButton(
        elementName = "smartbar-actions-overflow-customize-button",
    ),

    SmartbarActionsEditor(
        elementName = "smartbar-actions-editor",
    ),
    SmartbarActionsEditorHeader(
        elementName = "smartbar-actions-editor-header",
    ),
    SmartbarActionsEditorHeaderButton(
        elementName = "smartbar-actions-editor-header-button",
    ),
    SmartbarActionsEditorSubheader(
        elementName = "smartbar-actions-editor-subheader",
    ),
    SmartbarActionsEditorTileGrid(
        elementName = "smartbar-actions-editor-tile-grid",
    ),
    SmartbarActionsEditorTile(
        elementName = "smartbar-actions-editor-tile",
    ),

    SmartbarCandidatesRow(
        elementName = "smartbar-candidates-row",
    ),
    SmartbarCandidateWord(
        elementName = "smartbar-candidate-word",
    ),
    SmartbarCandidateWordText(
        elementName = "smartbar-candidate-word-text",
    ),
    SmartbarCandidateWordSecondaryText(
        elementName = "smartbar-candidate-word-secondary-text",
    ),
    SmartbarCandidateClip(
        elementName = "smartbar-candidate-clip",
    ),
    SmartbarCandidateClipIcon(
        elementName = "smartbar-candidate-clip-icon",
    ),
    SmartbarCandidateClipText(
        elementName = "smartbar-candidate-clip-text",
    ),
    SmartbarCandidateSpacer(
        elementName = "smartbar-candidate-spacer",
    ),

    SubtypePanel(
        elementName = "subtype-panel",
    ),
    SubtypePanelHeader(
        elementName = "subtype-panel-header",
    ),
    SubtypePanelList(
        elementName = "subtype-panel-list",
    ),
    SubtypePanelListItem(
        elementName = "subtype-panel-list-item",
    ),
    SubtypePanelListItemIconLeading(
        elementName = "subtype-panel-list-item-icon-leading",
    ),
    SubtypePanelListItemText(
        elementName = "subtype-panel-list-item-text",
    );

    companion object {
        val elementNames by lazy { entries.map { it.elementName } }

        val elementNamesToOrdinals by lazy {
            val enumEntries = entries
            buildMap {
                enumEntries.forEach { entry ->
                    put(entry.elementName, entry.ordinal)
                }
            }
        }

    }

    object Attr {
        const val Code = "code"
        const val Mode = "mode"
        const val ShiftState = "shiftstate"
        const val WindowMode = "windowmode"
    }
}
