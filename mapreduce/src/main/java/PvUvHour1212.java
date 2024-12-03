import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;

public class PvUvHour1212 {

    public static class HourMapper extends Mapper<Object, Text, Text, Text> {
        private Text hourKey = new Text();
        private Text userId = new Text();
        private boolean isFirstLine = true; // 用于跳过表头

        @Override
        protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            // 跳过表头
            if (isFirstLine) {
                isFirstLine = false;
                return;
            }

            String[] fields = value.toString().split(",");
            if (fields.length == 7) {
                String date = fields[5];   // 提取 date 字段
                String hour = fields[6];   // 提取 hour 字段
                String user = fields[0];   // 提取 user_id 字段

                // 过滤特定日期 2014-12-12
                if ("2014-12-12".equals(date)) {
                    hourKey.set(hour);
                    userId.set(user);
                    context.write(hourKey, userId); // 输出 <hour, user_id>
                }
            }
        }
    }

    public static class HourReducer extends Reducer<Text, Text, Text, Text> {
        private Text result = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            int pv = 0; // 页面浏览量
            HashSet<String> uniqueUsers = new HashSet<>(); // 用于存储唯一用户 ID

            for (Text value : values) {
                pv++;
                uniqueUsers.add(value.toString());
            }

            int uv = uniqueUsers.size(); // 计算唯一用户数
            result.set(pv + "\t" + uv);  // 输出格式：<PV> <UV>
            context.write(key, result); // 输出 <hour, "PV\tUV">
        }
    }

    public static void main(String[] args) throws Exception {
        long startTime = System.currentTimeMillis(); // 开始时间
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Hourly PV and UV for 12-12");

        job.setJarByClass(PvUvHour1212.class);
        job.setMapperClass(HourMapper.class);
        job.setReducerClass(HourReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        boolean jobCompleted = job.waitForCompletion(true);

        long endTime = System.currentTimeMillis(); // 结束时间
        long duration = endTime - startTime; // 计算运行时间

        // 将运行时间写入输出路径的文件
        if (jobCompleted) {
            Path outputPath = new Path(args[1] + "/execution_time.txt");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    outputPath.getFileSystem(conf).create(outputPath)))) {
                writer.write("Job Execution Time (ms): " + duration);
            }
        }

        System.exit(jobCompleted ? 0 : 1);
    }
}