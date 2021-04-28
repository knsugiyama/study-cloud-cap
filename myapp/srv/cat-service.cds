using my.bookshop as my from '../db/datamodel';

service cloud.sdk.capng {
     entity CapBusinessPartner as projection on my.CapBusinessPartner;
}