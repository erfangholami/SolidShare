package com.erfangholami.solidshare.domain.error

import androidx.annotation.StringRes
import com.erfangholami.solidshare.R

/**
 * What the user was trying to do when something failed.
 *
 * The operation supplies the headline ("Couldn't create the folder"); [AppError] supplies the
 * explanation that follows it. Splitting them this way keeps every message specific to the
 * moment it appears without writing one string per cause per screen.
 *
 * To add a screen or a feature: add an entry with its own [titleRes]. It carries everything the
 * error layer needs — no other file has to change.
 *
 * @property titleRes a short past-tense headline, e.g. "Couldn't upload the file".
 */
enum class AppOperation(@param:StringRes val titleRes: Int) {

    LOAD_FILES(R.string.op_load_files),
    OPEN_FOLDER(R.string.op_open_folder),
    OPEN_FILE(R.string.op_open_file),
    CREATE_FOLDER(R.string.op_create_folder),
    UPLOAD_FILE(R.string.op_upload_file),
    DOWNLOAD_FILE(R.string.op_download_file),
    DELETE_RESOURCE(R.string.op_delete_resource),
    DUPLICATE_RESOURCE(R.string.op_duplicate_resource),
    LOAD_RESOURCE_DETAILS(R.string.op_load_resource_details),

    LOAD_SHARES(R.string.op_load_shares),
    REFRESH_SHARES(R.string.op_refresh_shares),
    REBUILD_SHARE_INDEX(R.string.op_rebuild_share_index),
    CREATE_SHARE(R.string.op_create_share),
    UPDATE_SHARE_ACCESS(R.string.op_update_share_access),
    REVOKE_SHARE(R.string.op_revoke_share),
    CHECK_ACCESS(R.string.op_check_access),
    ADD_RECEIVED_SHARE(R.string.op_add_received_share),
    REMOVE_RECEIVED_SHARE(R.string.op_remove_received_share),
    REQUEST_ACCESS(R.string.op_request_access),
    OPEN_SHARED_ITEM(R.string.op_open_shared_item),
    COPY_SHARED_ITEM(R.string.op_copy_shared_item),

    LOAD_NOTIFICATIONS(R.string.op_load_notifications),
    DELETE_NOTIFICATION(R.string.op_delete_notification),
    ANSWER_ACCESS_REQUEST(R.string.op_answer_access_request),

    LOAD_CONTACTS(R.string.op_load_contacts),
    REFRESH_CONTACTS(R.string.op_refresh_contacts),
    LOAD_ADDRESS_BOOKS(R.string.op_load_address_books),
    SAVE_CONTACT(R.string.op_save_contact),
    DELETE_CONTACT(R.string.op_delete_contact),
    MERGE_CONTACTS(R.string.op_merge_contacts),
    IMPORT_CONTACTS(R.string.op_import_contacts),
    EXPORT_CONTACTS(R.string.op_export_contacts),
    SYNC_CONTACTS(R.string.op_sync_contacts),

    LOAD_WALLET(R.string.op_load_wallet),
    LOAD_TICKET(R.string.op_load_ticket),
    SAVE_TICKET(R.string.op_save_ticket),
    DELETE_TICKET(R.string.op_delete_ticket),
    IMPORT_TICKET(R.string.op_import_ticket),
    REFRESH_PASS(R.string.op_refresh_pass),

    SIGN_IN(R.string.op_sign_in),
    SIGN_OUT(R.string.op_sign_out),
    SWITCH_ACCOUNT(R.string.op_switch_account),
    LOAD_POD_PROVIDERS(R.string.op_load_pod_providers),
    LOAD_PROFILE(R.string.op_load_profile),
    UPDATE_PROFILE(R.string.op_update_profile),

    SCAN_CODE(R.string.op_scan_code),
    SAVE_TO_DEVICE(R.string.op_save_to_device),
    SYNC_PENDING_CHANGES(R.string.op_sync_pending_changes),

    GENERIC(R.string.op_generic),
}
