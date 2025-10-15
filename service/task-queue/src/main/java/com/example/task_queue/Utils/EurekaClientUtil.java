package com.example.task_queue.Utils;

import org.springframework.cloud.client.discovery.DiscoveryClient;

public class EurekaClientUtil {
    private final DiscoveryClient discoveryClient;

    public EurekaClientUtil(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }
}
