-- Outbox'a satır eklenince 'outbox' kanalına bildirim. Relay bu kanalı dinler ve anında uyanır;
-- polling sadece yedek olarak kalır.
--
-- NOTIFY transactional'dır: bildirim ancak transaction COMMIT olunca gönderilir. Relay, henüz
-- commit olmamış (görünmeyen) bir satır için boşuna uyanmaz. Aynı transaction'daki birden fazla
-- aynı bildirim tek bildirime indirgenir.
CREATE FUNCTION notify_outbox() RETURNS trigger AS
$$
BEGIN
    PERFORM pg_notify('outbox', '');
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- FOR EACH STATEMENT: bir INSERT ifadesi kaç satır eklerse eklesin tek bildirim.
CREATE TRIGGER outbox_inserted
    AFTER INSERT ON outbox
    FOR EACH STATEMENT
EXECUTE FUNCTION notify_outbox();
