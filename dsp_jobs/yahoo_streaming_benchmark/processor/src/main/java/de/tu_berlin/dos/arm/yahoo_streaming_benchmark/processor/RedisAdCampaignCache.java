package de.tu_berlin.dos.arm.yahoo_streaming_benchmark.processor;

import redis.clients.jedis.Jedis;

import java.util.HashMap;

public class RedisAdCampaignCache {
    private Jedis jedis;
    private HashMap<String, String> ad_to_campaign;

    public RedisAdCampaignCache(String redisServerHostname) {
        jedis = new Jedis(redisServerHostname);
    }

    public void prepare() {
        ad_to_campaign = new HashMap<>();
    }

    public String execute(String ad_id) {
        String campaign_id = ad_to_campaign.get(ad_id);
        if(campaign_id == null) {
            campaign_id = jedis.get(ad_id);
            if(campaign_id == null) {
                return null;
            }
            else {
                ad_to_campaign.put(ad_id, campaign_id);
            }
        }
        return campaign_id;
    }
}
