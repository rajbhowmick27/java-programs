package com.practice.javaprograms.graphs;

import java.lang.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Objects;


public class FloodFill {
    public static void main(String[] args) throws IOException, URISyntaxException {
//        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        URI uri = Objects.requireNonNull(ClassLoader.getSystemClassLoader().getResource("data/testInput.txt")).toURI();
        BufferedReader br =
                new BufferedReader(new FileReader(Paths.get(uri).toFile()));
        int T = Integer.parseInt(br.readLine().trim());
        while(T-->0)
        {
            String[] S1 = br.readLine().trim().split(" ");
            int n = Integer.parseInt(S1[0]);
            int m = Integer.parseInt(S1[1]);
            int[][] image =  new int[n][m];
            for(int i = 0; i < n; i++){
                String[] S2 = br.readLine().trim().split(" ");
                for(int j = 0; j < m; j++)
                    image[i][j] = Integer.parseInt(S2[j]);
            }
            String[] S3 = br.readLine().trim().split(" ");
            int sr = Integer.parseInt(S3[0]);
            int sc = Integer.parseInt(S3[1]);
            int newColor = Integer.parseInt(S3[2]);
            FloodFillSolution obj = new FloodFillSolution();
            int[][] ans = obj.floodFill(image, sr, sc, newColor);
            for(int i = 0; i < ans.length; i++){
                for(int j = 0; j < ans[i].length; j++)
                    System.out.print(ans[i][j] + " ");
                System.out.println();
            }
        }
    }
}

// } Driver Code Ends


class FloodFillSolution
{
    private int[] delRow = {-1,0,1,0};
    private int[] delCol = {0,1,0,-1};
    private void dfs(int row,int col,int initialColor,int newColor,int[][] ans,int [][] image){
        ans[row][col] = newColor;

        int n = ans.length;
        int m = ans[0].length;

        for(int i=0;i<4;i++){
            int nRow = row + delRow[i];
            int nCol = col + delCol[i];

            if(nRow>=0 && nRow<n && nCol>=0 && nCol<m
                    && image[nRow][nCol] == initialColor && ans[nRow][nCol] != newColor){
                dfs(nRow,nCol,initialColor,newColor,ans,image);
            }
        }
    }
    public int[][] floodFill(int[][] image, int sr, int sc, int newColor)
    {
        // Code here
        int initialColor = image[sr][sc];

        int[][] ans = image;

        dfs(sr,sc,initialColor,newColor,ans,image);

        return ans;
    }
}