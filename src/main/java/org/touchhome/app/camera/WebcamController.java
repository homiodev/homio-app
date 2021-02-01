package org.touchhome.app.camera;

import com.github.sarxos.webcam.Webcam;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.camera.entity.IpCameraEntity;
import org.touchhome.app.camera.openhub.GroupTracker;
import org.touchhome.app.camera.openhub.handler.IpCameraHandler;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.wifi.WirelessHardwareRepository;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/rest/video")
@RequiredArgsConstructor
public class WebcamController {

    private final WirelessHardwareRepository wirelessHardwareRepository;

    // private final ImageManager imageManager;
    private final EntityContext entityContext;
    // private final VideoStreamService videoStreamService;
    // private final Map<String, ControlledStream> streams = new HashMap<>();

    private final GroupTracker groupTracker = new GroupTracker();
    private Map<String, IpCameraHandler> runningCameras = new HashMap<>();

    @PostConstruct
    public void init() {
        // listen if camera entity removed but run
        entityContext.addEntityRemovedListener(IpCameraEntity.class, ipCameraEntity -> {
            if (runningCameras.containsKey(ipCameraEntity.getEntityID())) {
                runningCameras.get(ipCameraEntity.getEntityID()).dispose();
            }
        });
        // lister start/stop status
        entityContext.addEntityUpdateListener(IpCameraEntity.class, ipCameraEntity -> {
            if (ipCameraEntity.isStart() && !runningCameras.containsKey(ipCameraEntity.getEntityID())) {
                IpCameraHandler ipCameraHandler = new IpCameraHandler(ipCameraEntity, entityContext,
                        wirelessHardwareRepository.getIPAddress(), groupTracker);
                ipCameraHandler.initialize();
                runningCameras.put(ipCameraEntity.getEntityID(), ipCameraHandler);
                // run camera
            } else if (!ipCameraEntity.isStart() && runningCameras.containsKey(ipCameraEntity.getEntityID())) {
                runningCameras.remove(ipCameraEntity.getEntityID()).dispose();
            }
        });
    }

    @GetMapping("isCameraDetected")
    public Boolean isCameraDetected() {
        return Webcam.getDefault() != null;
    }

/*    @GetMapping("snapshot")
    public ResponseEntity<InputStreamResource> takeSnapshot(@RequestParam(value = "width", defaultValue = "640") Integer width,
                                                            @RequestParam(value = "height", defaultValue = "480") Integer height,
                                                            @RequestParam(value = "frameRate", defaultValue = "24") Integer frameRate) throws IOException {
        // return imageManager.getImage(videoManager.takeSnapshot(width, height));
        return null;
    }*/

    /*@GetMapping("/stream/{fileName:.+}")
    public ResponseEntity<byte[]> streamVideo(@RequestHeader(value = "Range", required = false) String httpRangeList,
                                              @PathVariable("fileName") String fileName) {
        return videoStreamService.prepareContent(fileName, httpRangeList);
    }*/

/*    @RequiredArgsConstructor
    private class WebcamStream {
        private final Webcam webcam;
    }*/

//    private Map<String, WebcamStream> streams = new HashMap<>();

    /*@GetMapping(value = "stream/{name}")
    public ResponseEntity<byte[]> streamVideo(@PathVariable(value = "name") String name,
                                              @RequestHeader(value = "Range", required = false) String range,
                                              @RequestParam(value = "width", defaultValue = "320") Integer width,
                                              @RequestParam(value = "height", defaultValue = "240") Integer height,
                                              @RequestParam(value = "sendHeader", defaultValue = "true") Boolean sendHeader,
                                              @RequestParam(value = "fragmentSequence", defaultValue = "-1") Integer fragmentSequence,
                                              @RequestParam(value = "singleFragment", defaultValue = "false") Boolean singleFragment,
                                              @RequestParam(value = "rate", required = false) Integer frameRate,
                                              HttpServletRequest httpServletRequest,
                                              HttpServletResponse httpServletResponse) throws IOException {
        if (!streams.containsKey(name)) {
            Webcam webcam = Webcam.getWebcamByName(name);
            if (webcam == null) {
                throw new ServerException("Camera " + name + " not found");
            }
            Dimension dimension = new Dimension(width, height);
            webcam.setViewSize(dimension);
            streams.put(name, new WebcamStream(webcam));
        }

        long rangeStart = 0;
        long rangeEnd;
        byte[] data;
        Long fileSize;
        //  MimeTypeUtils.probeContentType(filepath)
        // String fileType = FilenameUtils.getExtension(fullFileName);
        BufferedImage bufferedImage = streams. webcam.getImage();
        try {
            //   fileSize = getFileSize(fullFileName);
            if (range == null) {
                return ResponseEntity.status(HttpStatus.OK)
                        .header(CONTENT_TYPE, "video/mp4")
                        .header(CONTENT_LENGTH, String.valueOf(fileSize))
                        .body(readByteRange(fullFileName, rangeStart, fileSize - 1)); // Read the object and convert it as bytes
            }
            String[] ranges = range.split("-");
            rangeStart = Long.parseLong(ranges[0].substring(6));
            if (ranges.length > 1) {
                rangeEnd = Long.parseLong(ranges[1]);
            } else {
                rangeEnd = fileSize - 1;
            }
            if (fileSize < rangeEnd) {
                rangeEnd = fileSize - 1;
            }
            data = readByteRange(fullFileName, rangeStart, rangeEnd);
        } catch (IOException e) {
            log.error("Exception while reading the file {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        String contentLength = String.valueOf((rangeEnd - rangeStart) + 1);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(CONTENT_TYPE, "video/mp4")
                .header(ACCEPT_RANGES, BYTES)
                .header(CONTENT_LENGTH, contentLength)
                .header(CONTENT_RANGE, BYTES + " " + rangeStart + "-" + rangeEnd + "/" + fileSize)
                .body(data);

        //  StreamServerAgent serverAgent = new StreamServerAgent(webcam, dimension);
        // TODO:     serverAgent.start(new InetSocketAddress("localhost", 20000));
    }*/

    @GetMapping("isStream")
    public Boolean isStreamVideo() {
        return false;
    }

    @GetMapping("stop")
    public void stopVideo() {

    }

    @GetMapping("name")
    public String getCameraName() {
        return Webcam.getDefault().getName();
    }

    /*public Path takeSnapshot(int width, int height) throws IOException {
        Webcam webcam = Webcam.getDefault();
        try {
            //  webcam.getViewSizes()
            webcam.setViewSize(new Dimension(width, height));
            webcam.open();
            BufferedImage image = webcam.getImage();
            // save image to PNG file
            Path imagePath = imagesDir.resolve("CameraImage_" + TouchHomeUtils.getTimestampString() + ".png");
            ImageIO.write(image, "PNG", imagePath.toFile());
            return imagePath;
        } finally {
            webcam.close();
        }
    }*/

    /*@GetMapping("{name}.m3u8")
    public void getPlaylist(@PathVariable("name") String name, HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.debug("Playlist requested");
		*//*
		 * EXT-X-MEDIA-SEQUENCE
		 * Each media file URI in a Playlist has a unique sequence number.  The sequence number
		 * of a URI is equal to the sequence number of the URI that preceded it plus one. The
		 * EXT-X-MEDIA-SEQUENCE tag indicates the sequence number of the first URI that appears
		 * in a Playlist file.
		 *
		 	#EXTM3U
		 	#EXT-X-ALLOW-CACHE:NO
			#EXT-X-MEDIA-SEQUENCE:0
			#EXT-X-TARGETDURATION:10
			#EXTINF:10,
			http://media.example.com/segment1.ts
			#EXTINF:10,
			http://media.example.com/segment2.ts
			#EXTINF:10,
			http://media.example.com/segment3.ts
			#EXT-X-ENDLIST

			Using one large file, testing with ipod touch, this worked (149 == 2:29)
			#EXTM3U
			#EXT-X-TARGETDURATION:149
			#EXT-X-MEDIA-SEQUENCE:0
			#EXTINF:149, no desc
			out0.ts
			#EXT-X-ENDLIST

			Using these encoding parameters:
			ffmpeg -i test.mp4 -re -an -vcodec libx264 -b 96k -flags +loop -cmp +chroma -partitions +parti4x4+partp8x8+partb8x8
			-subq 5 -trellis 1 -refs 1 -coder 0 -me_range 16 -keyint_min 25 -sc_threshold 40 -i_qfactor 0.71 -bt 200k -maxrate 96k
			-bufsize 96k -rc_eq 'blurCplx^(1-qComp)' -qcomp 0.6 -qmin 10 -qmax 51 -qdiff 4 -level 30 -aspect 320:240 -g 30 -async 2
			-s 320x240 -f mpegts out.ts
			Suggested by others for 128k
			ffmpeg -d -i 'rtmp://123.123.117.16:1935/live/abcdpc2 live=1' -re -g 250 -keyint_min 25 -bf 0 -me_range 16 -sc_threshold 40 -cmp 256 -coder 0 -trellis 0 -subq 6 -refs 5 -r 25 -c:a libfaac -ab:a 48k -async 1 -ac:a 2 -c:v libx264 -profile baseline -s:v 320x180 -b:v 96k -aspect:v 16:9 -map 0 -ar 22050 -vbsf h264_mp4toannexb -flags -global_header -f segment -segment_time 10 -segment_format mpegts /dev/shm/stream128ios%09d.ts 2>/dev/null
		 *//*
        // get red5 context and segmenter

        // path
        //get the requested stream
        log.debug("Request for stream: {} playlist", name);
        //check for the stream
        if (!segmenterService.isAvailable(name)) {
            WebCamReader webCamReader = new WebCamReader(entityContext, Webcam.getDefault());
            segmenterService.start("default", "test", webCamReader);
        }

        if (segmenterService.isAvailable(name)) {
            log.debug("Stream: {} is available", name);
            // get the segment count
            int count = segmenterService.getSegmentCount(name);
            log.debug("Segment count: {}", count);
            // check for minimum segment count and if we dont match or exceed
            // wait for (minimum segment count * segment duration) before returning
            if (count < minimumSegmentCount) {
                log.debug("Starting wait loop for segment availability");
                long maxWaitTime = minimumSegmentCount * segmenterService.getSegmentTimeLimit();
                long start = System.currentTimeMillis();
                do {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {
                    }
                    if ((System.currentTimeMillis() - start) >= maxWaitTime) {
                        log.info("Maximum segment wait time exceeded for {}", name);
                        break;
                    }
                } while ((count = segmenterService.getSegmentCount(name)) < minimumSegmentCount);
            }
			*//*
			HTTP streaming spec section 3.2.2
			Each media file URI in a Playlist has a unique sequence number.  The sequence number of a URI is equal to the sequence number
			of the URI that preceded it plus one. The EXT-X-MEDIA-SEQUENCE tag indicates the sequence number of the first URI that appears
			in a Playlist file.
			*//*
            // get the completed segments
            Segment[] segments = segmenterService.getSegments(name);
            if (segments != null && segments.length > 0) {
                //write the playlist
                PrintWriter writer = response.getWriter();
                // set proper content type
                response.setContentType("application/x-mpegURL");
                // for the m3u8 content
                StringBuilder sb = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-ALLOW-CACHE:NO\n");
                // get segment duration in seconds
                long segmentDuration = segmenterService.getSegmentTimeLimit() / 1000;
                // create the heading
                sb.append(String.format("#EXT-X-TARGETDURATION:%s\n#EXT-X-MEDIA-SEQUENCE:%s\n", segmentDuration, segments[0].getIndex()));
                // loop through them
                for (Segment segment : segments) {
                    // get sequence number
                    int sequenceNumber = segment.getIndex();
                    log.trace("Sequence number: {}", sequenceNumber);
                    sb.append(String.format("#EXTINF:%.1f, segment\n%s_%s.ts\n", segment.getDuration(), name, sequenceNumber));
                    // are we on the last segment?
                    if (segment.isLast()) {
                        log.debug("Last segment");
                        sb.append("#EXT-X-ENDLIST\n");
                        break;
                    }
                }
                final String m3u8 = sb.toString();
                log.debug("Playlist for: {}\n{}", name, m3u8);
                writer.write(m3u8);
                writer.flush();
            } else {
                log.trace("Minimum segment count not yet reached, currently at: {}", count);
                response.setIntHeader("Retry-After", 60);
                response.sendError(503, "Not enough segments available for " + name);
            }
        } else {
            log.debug("Stream: {} is not available", name);
            response.sendError(404, "No playlist for " + name);
        }
    }*/

    /*@GetMapping("transportSegmentFeeder")
    protected void transportSegmentFeeder(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.debug("Segment feed requested");
        // get red5 context and segmenter
        // get the requested stream / segment
        String servletPath = request.getServletPath();
        String streamName = servletPath.split("\\.")[0];
        log.debug("Stream name: {}", streamName);
        if (segmenterService.isAvailable(streamName)) {
            response.setContentType("video/MP2T");
            // data segment
            Segment segment;
            // setup buffers and output stream
            byte[] buf = new byte[188];
            ByteBuffer buffer = ByteBuffer.allocate(188);
            ServletOutputStream sos = response.getOutputStream();
            // loop segments
            while ((segment = segmenterService.getSegment(streamName)) != null) {
                do {
                    buffer = segment.read(buffer);
                    // log.trace("Limit - position: {}", (buffer.limit() - buffer.position()));
                    if ((buffer.limit() - buffer.position()) == 188) {
                        buffer.get(buf);
                        // write down the output stream
                        sos.write(buf);
                    } else {
                        log.info("Segment result has indicated a problem");
                        // verifies the currently requested stream segment
                        // number against the currently active segment
                        if (segmenterService.getSegment(streamName) == null) {
                            log.debug("Requested segment is no longer available");
                            break;
                        }
                    }
                    buffer.clear();
                } while (segment.hasMoreData());
                log.trace("Segment {} had no more data", segment.getIndex());
                // flush
                sos.flush();
                // segment had no more data
                segment.cleanupThreadLocal();
            }
            buffer.clear();
        } else {
            // let requester know that stream segment is not available
            response.sendError(404, "Requested segmented stream not found");
        }
    }*/

   /* @GetMapping("transportSegment")
    public void transportSegment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.debug("Segment requested");
        // get red5 context and segmenter
       *//* if (service == null) {
            ApplicationContext appCtx = (ApplicationContext) getServletContext().getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
            service = (SegmenterService) appCtx.getBean("segmenter.service");
        }*//*
        //get the requested stream / segment
        String servletPath = request.getServletPath();
        String[] path = servletPath.split("\\.");
        log.trace("Path parts: {}", path.length);
        //fail if they request the same segment
        HttpSession session = request.getSession(false);
        if (session != null) {
            String stream = (String) session.getAttribute("stream");
            if (path[0].equals(stream)) {
                log.info("Segment {} was already played by this requester", stream);
                return;
            }
            session.setAttribute("stream", path[0]);
        }
        // look for underscore char
        int digitIndex = path[0].lastIndexOf('_') + 1;
        String streamName = path[0].substring(1, digitIndex - 1);
        int sequenceNumber = Integer.parseInt(path[0].substring(digitIndex));
        log.debug("Stream name: {} sequence: {}", streamName, sequenceNumber);
        if (segmenterService.isAvailable(streamName)) {
            response.setContentType("video/MP2T");
            Segment segment = segmenterService.getSegment(streamName, sequenceNumber);
            if (segment != null) {
                byte[] buf = new byte[188];
                ByteBuffer buffer = ByteBuffer.allocate(188);
                ServletOutputStream sos = response.getOutputStream();
                do {
                    buffer = segment.read(buffer);
                    //log.trace("Limit - position: {}", (buffer.limit() - buffer.position()));
                    if ((buffer.limit() - buffer.position()) == 188) {
                        buffer.get(buf);
                        //write down the output stream
                        sos.write(buf);
                    } else {
                        log.info("Segment result has indicated a problem");
                        // verifies the currently requested stream segment number against the  currently active segment
                        if (segmenterService.getSegment(streamName, sequenceNumber) == null) {
                            log.debug("Requested segment is no longer available");
                            break;
                        }
                    }
                    buffer.clear();
                } while (segment.hasMoreData());
                log.trace("Segment {} had no more data", segment.getIndex());
                // flush
                sos.flush();
                // segment had no more data
                segment.cleanupThreadLocal();
            } else {
                log.info("Segment for {} was not found", streamName);
            }
        } else {
            //TODO let requester know that stream segment is not available
            response.sendError(404, "Segment not found");
        }*/
//    }
}
